/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.huangwei.ai.ragent.imitation.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huangwei.ai.ragent.framework.convention.ChatMessage;
import com.huangwei.ai.ragent.framework.convention.ChatRequest;
import com.huangwei.ai.ragent.framework.convention.RetrievedChunk;
import com.huangwei.ai.ragent.framework.context.UserContext;
import com.huangwei.ai.ragent.imitation.controller.request.ArticleRewriteRequest;
import com.huangwei.ai.ragent.imitation.dao.entity.ArticleAnalysisDO;
import com.huangwei.ai.ragent.imitation.dao.mapper.ArticleAnalysisMapper;
import com.huangwei.ai.ragent.imitation.service.ImitationService;
import com.huangwei.ai.ragent.infra.chat.LLMService;
import com.huangwei.ai.ragent.infra.chat.StreamCallback;
import com.huangwei.ai.ragent.ingestion.domain.enums.IngestionStatus;
import com.huangwei.ai.ragent.ingestion.domain.result.IngestionResult;
import com.huangwei.ai.ragent.ingestion.service.IngestionTaskService;
import com.huangwei.ai.ragent.rag.config.RAGDefaultProperties;
import com.huangwei.ai.ragent.rag.core.memory.ConversationMemoryService;
import com.huangwei.ai.ragent.rag.core.retrieve.MilvusRetrieverService;
import com.huangwei.ai.ragent.rag.core.retrieve.RetrieveRequest;
import com.huangwei.ai.ragent.rag.service.ConversationMessageService;
import com.huangwei.ai.ragent.rag.service.bo.ConversationMessageBO;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.response.QueryResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImitationServiceImpl implements ImitationService {

    private final IngestionTaskService ingestionTaskService;
    private final MilvusRetrieverService milvusRetrieverService;
    private final MilvusClientV2 milvusClient;
    private final RAGDefaultProperties ragDefaultProperties;
    private final ArticleAnalysisMapper articleAnalysisMapper;
    private final ConversationMemoryService conversationMemoryService;
    private final ConversationMessageService conversationMessageService;
    private final LLMService llmService;

    private static final int MAX_UPLOAD_CHARS = 5000;

    @Override
    public IngestionResult upload(String pipelineId, MultipartFile file) {
        log.info("文章仿写文件上传，pipelineId={}, fileName={}", pipelineId, file.getOriginalFilename());

        // 0. 读取文件内容，校验字数
        String rawText;
        try {
            rawText = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return IngestionResult.builder().status(IngestionStatus.FAILED).message("读取文件失败: " + e.getMessage()).build();
        }
        int wordCount = rawText.trim().length();
        if (wordCount > MAX_UPLOAD_CHARS) {
            return IngestionResult.builder()
                    .status(IngestionStatus.FAILED)
                    .message("文件字数（" + wordCount + "字）超过上限 " + MAX_UPLOAD_CHARS + " 字")
                    .originalWordCount(wordCount)
                    .build();
        }

        // 1. 走 ingestion pipeline：解析 -> 分块 -> 向量化 -> 写入 Milvus
        IngestionResult result = ingestionTaskService.upload(pipelineId, file);
        result.setOriginalWordCount(wordCount);

        // 2. 入库成功后，从 Milvus 取出所有 chunk 拼接原文，提取文章分析摘要
        if (result.getStatus() != null && result.getChunkCount() != null && result.getChunkCount() > 0) {
            try {
                extractAndSaveAnalysis(result.getTaskId(), file.getOriginalFilename());
            } catch (Exception e) {
                log.error("文章分析提取失败，不影响入库结果，taskId={}", result.getTaskId(), e);
            }
        }

        return result;
    }

    @Override
    public void rewrite(ArticleRewriteRequest request, SseEmitter emitter) {
        // 0. 会话ID：为空则新建
        String conversationId = StrUtil.isBlank(request.getConversationId())
                ? IdUtil.getSnowflakeNextIdStr() : request.getConversationId();
        String userId = UserContext.getUserId();

        // 1. 存储用户消息（改写要求）
        String userMsg = StrUtil.isNotBlank(request.getRequirements())
                ? request.getRequirements() : "请对参考文章进行仿写";
        conversationMemoryService.append(conversationId, userId, ChatMessage.user(userMsg));

        // 2. 加载本次会话的历史消息
        List<ChatMessage> history = conversationMemoryService.load(conversationId, userId);

        // 3. 获取参考文章内容：优先按 task_id 全量查询，降级为语义检索
        String fullText;
        if (StrUtil.isNotBlank(request.getTaskId())) {
            fullText = fetchAllChunksByTaskId(request.getTaskId());
        } else {
            fullText = null;
        }

        if (StrUtil.isBlank(fullText)) {
            // 降级：语义检索
            String queryText = StrUtil.isNotBlank(request.getRequirements())
                    ? request.getRequirements() : "文章内容";
            RetrieveRequest retrieveRequest = RetrieveRequest.builder()
                    .query(queryText)
                    .topK(request.getTopK())
                    .collectionName(request.getCollectionName())
                    .build();
            List<RetrievedChunk> chunks = milvusRetrieverService.retrieve(retrieveRequest);

            if (chunks.isEmpty()) {
                try {
                    emitter.send(SseEmitter.event().data("未检索到参考文章内容，请确认已上传文章文件并完成入库。"));
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
                return;
            }

            fullText = chunks.stream()
                    .map(RetrievedChunk::getText)
                    .collect(Collectors.joining("\n\n"));
        }

        // 5. 查询文章分析摘要（核心论点、写作风格、文章结构）
        ArticleAnalysisDO analysis = findLatestAnalysis();
        String analysisContext = buildAnalysisContext(analysis);

        // 6. 组装 prompt（带历史对话上下文）
        String systemPrompt = buildSystemPrompt(request.getWordCount());
        String userPrompt = buildUserPrompt(analysisContext, fullText, request.getRequirements());

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        for (ChatMessage historyMsg : history) {
            messages.add(historyMsg);
        }
        messages.add(ChatMessage.user(userPrompt));

        // 构建 prompt 快照
        String promptSnapshot = buildPromptSnapshot(messages);

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .modelId(request.getModelId())
                .temperature(0.7)
                .topP(0.9)
                .build();

        // 7. 流式调用 LLM
        StringBuilder fullResponse = new StringBuilder();
        llmService.streamChat(chatRequest, new StreamCallback() {
            @Override
            public void onContent(String content) {
                fullResponse.append(content);
                try {
                    emitter.send(SseEmitter.event().data(content));
                } catch (Exception e) {
                    log.warn("SSE 发送失败", e);
                }
            }

            @Override
            public void onComplete() {
                try {
                    ConversationMessageBO assistantMsg = ConversationMessageBO.builder()
                            .conversationId(conversationId)
                            .userId(userId)
                            .role("assistant")
                            .content(fullResponse.toString())
                            .promptSnapshot(promptSnapshot)
                            .build();
                    conversationMessageService.addMessage(assistantMsg);
                } catch (Exception e) {
                    log.error("存储仿写结果失败", e);
                }
                try {
                    emitter.send(SseEmitter.event().name("meta")
                            .data("{\"conversationId\":\"" + conversationId + "\"}"));
                    emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                    emitter.complete();
                } catch (Exception e) {
                    log.warn("SSE 完成信号发送失败", e);
                }
            }

            @Override
            public void onError(Throwable error) {
                log.error("文章仿写 LLM 调用失败", error);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data("仿写生成失败: " + error.getMessage()));
                } catch (Exception e) {
                    // ignore
                }
                emitter.completeWithError(error);
            }
        });
    }

    // ==================== 原文全量查询 ====================

    /**
     * 按 task_id 从 Milvus 查询该文章的所有 chunk，按 chunk_index 排序后拼接为完整原文
     */
    private String fetchAllChunksByTaskId(String taskId) {
        String collectionName = ragDefaultProperties.getCollectionName();

        QueryReq queryReq = QueryReq.builder()
                .collectionName(collectionName)
                .filter("metadata[\"task_id\"] == \"" + taskId + "\"")
                .outputFields(List.of("content", "metadata"))
                .limit(500)
                .build();

        QueryResp resp = milvusClient.query(queryReq);
        List<QueryResp.QueryResult> results = resp.getQueryResults();
        if (results == null || results.isEmpty()) {
            log.warn("按 task_id 未查询到 chunk，taskId={}", taskId);
            return null;
        }

        // 按 chunk_index 排序
        results.sort((a, b) -> {
            int idxA = getChunkIndex(a);
            int idxB = getChunkIndex(b);
            return Integer.compare(idxA, idxB);
        });

        String fullText = results.stream()
                .map(r -> String.valueOf(r.getEntity().get("content")))
                .collect(Collectors.joining("\n"));

        log.info("按 task_id 获取原文成功，taskId={}, chunks={}, length={}", taskId, results.size(), fullText.length());
        return fullText;
    }

    private int getChunkIndex(QueryResp.QueryResult result) {
        try {
            Object meta = result.getEntity().get("metadata");
            if (meta instanceof Map) {
                Object idx = ((Map<?, ?>) meta).get("chunk_index");
                if (idx instanceof Number) {
                    return ((Number) idx).intValue();
                }
            }
        } catch (Exception e) {
            log.warn("解析 chunk_index 失败", e);
        }
        return 0;
    }

    // ==================== 文章分析提取 ====================

    /**
     * 从 Milvus 取出该任务的所有 chunk，拼接后让 LLM 提取文章分析摘要，存入 DB
     */
    private void extractAndSaveAnalysis(String taskId, String fileName) {
        String collectionName = ragDefaultProperties.getCollectionName();

        QueryReq queryReq = QueryReq.builder()
                .collectionName(collectionName)
                .filter("metadata[\"task_id\"] == \"" + taskId + "\"")
                .outputFields(List.of("content"))
                .limit(500)
                .build();

        QueryResp resp = milvusClient.query(queryReq);
        List<QueryResp.QueryResult> results = resp.getQueryResults();
        if (results == null || results.isEmpty()) {
            log.warn("未从 Milvus 查询到 chunk，taskId={}", taskId);
            return;
        }

        String fullText = results.stream()
                .map(r -> String.valueOf(r.getEntity().get("content")))
                .collect(Collectors.joining("\n"));

        if (StrUtil.isBlank(fullText)) {
            log.warn("拼接后的文本为空，taskId={}", taskId);
            return;
        }

        if (fullText.length() > 8000) {
            fullText = fullText.substring(0, 8000);
        }

        // 调用 LLM 提取三个维度的分析
        String keyPoints = extractByLLM("核心论点与要点", fullText);
        String writingStyle = extractByLLM("写作风格", fullText);
        String structure = extractByLLM("文章结构", fullText);

        ArticleAnalysisDO analysis = ArticleAnalysisDO.builder()
                .taskId(taskId)
                .fileName(fileName)
                .keyPoints(keyPoints)
                .writingStyle(writingStyle)
                .structure(structure)
                .build();
        articleAnalysisMapper.insert(analysis);
        log.info("文章分析提取完成，taskId={}, fileName={}", taskId, fileName);
    }

    private String extractByLLM(String aspect, String text) {
        String prompt = """
                请从以下参考文章中提取【%s】的分析信息。

                要求：
                - 用简洁的条目式列出关键信息
                - 核心论点：列出文章的主要观点、论据、结论
                - 写作风格：分析用词特点、句式偏好、语气基调、修辞手法
                - 文章结构：梳理段落组织方式、逻辑脉络、论述层次、过渡方式
                - 如果原文中没有相关信息，返回"暂无相关信息"
                - 不要加任何解释说明，直接输出分析内容
                - 控制在 500 字以内

                参考文章：
                %s
                """.formatted(aspect, text);

        ChatRequest req = ChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .temperature(0.3)
                .build();
        return llmService.chat(req);
    }

    // ==================== 分析查询 ====================

    private ArticleAnalysisDO findLatestAnalysis() {
        return articleAnalysisMapper.selectOne(
                new LambdaQueryWrapper<ArticleAnalysisDO>()
                        .eq(ArticleAnalysisDO::getDeleted, 0)
                        .orderByDesc(ArticleAnalysisDO::getCreateTime)
                        .last("LIMIT 1")
        );
    }

    private String buildAnalysisContext(ArticleAnalysisDO analysis) {
        if (analysis == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(analysis.getKeyPoints())) {
            sb.append("【核心论点与要点】\n").append(analysis.getKeyPoints()).append("\n\n");
        }
        if (StrUtil.isNotBlank(analysis.getWritingStyle())) {
            sb.append("【写作风格】\n").append(analysis.getWritingStyle()).append("\n\n");
        }
        if (StrUtil.isNotBlank(analysis.getStructure())) {
            sb.append("【文章结构】\n").append(analysis.getStructure()).append("\n\n");
        }
        return sb.toString();
    }

    // ==================== Prompt ====================

    private String buildSystemPrompt(int wordCount) {
        return """
                你是一位专业的文章仿写专家。你的任务是根据提供的参考文章，生成一篇改写后的文章。

                规则（按优先级从高到低）：
                1. 【最高优先级】用户的改写要求是一切的出发点，必须严格遵从。如果用户要求改变风格、语气、结构或大幅调整内容，以用户要求为准
                2. 仅在用户未特别指定的情况下，才默认保持原文的逻辑结构、写作风格和行文脉络
                3. 通过同义词替换、句式变换、语序调整等方法改写，使表达与原文不同
                4. 保留原文的核心观点和关键信息，不遗漏重要论点
                5. 不要引入原文中不存在的观点或信息
                6. 改写后的文章长度约 %d 字
                7. 直接输出改写后的文章，不要加任何解释说明、标题标记或"以下是改写结果"之类的前缀
                """.formatted(wordCount);
    }

    private String buildUserPrompt(String analysisContext, String chunkContext, String requirements) {
        StringBuilder sb = new StringBuilder();

        // 用户要求放在最前面，作为最高优先级指令
        if (StrUtil.isNotBlank(requirements)) {
            sb.append("【用户改写要求】（最高优先级，必须严格遵从）\n");
            sb.append(requirements);
            sb.append("\n\n----------\n\n");
        }

        if (StrUtil.isNotBlank(analysisContext)) {
            sb.append("【原文参考信息】（以下内容仅供参考，如果与上面的用户要求冲突，以用户要求为准）\n\n");
            sb.append(analysisContext);
            sb.append("----------\n\n");
        }

        sb.append("【参考文章完整原文】\n\n");
        sb.append(chunkContext);
        sb.append("\n\n----------\n\n");

        if (StrUtil.isNotBlank(requirements)) {
            sb.append("请严格按照上述用户要求，对参考文章进行改写。");
        } else {
            sb.append("请对参考文章进行仿写改写，默认保持原文的逻辑结构和行文脉络，通过同义词替换、语序调整等方式使表达有所不同。");
        }

        return sb.toString();
    }

    private String buildPromptSnapshot(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            sb.append("[").append(msg.getRole().name()).append("]\n");
            sb.append(msg.getContent()).append("\n\n");
        }
        return sb.toString();
    }
}
