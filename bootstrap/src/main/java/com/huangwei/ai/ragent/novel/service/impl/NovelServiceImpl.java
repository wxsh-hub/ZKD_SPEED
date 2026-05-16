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

package com.huangwei.ai.ragent.novel.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.huangwei.ai.ragent.framework.convention.ChatMessage;
import com.huangwei.ai.ragent.framework.convention.ChatRequest;
import com.huangwei.ai.ragent.framework.convention.RetrievedChunk;
import com.huangwei.ai.ragent.framework.context.UserContext;
import com.huangwei.ai.ragent.infra.chat.LLMService;
import com.huangwei.ai.ragent.infra.chat.StreamCallback;
import com.huangwei.ai.ragent.ingestion.domain.enums.IngestionStatus;
import com.huangwei.ai.ragent.ingestion.domain.result.IngestionResult;
import com.huangwei.ai.ragent.ingestion.service.IngestionTaskService;
import com.huangwei.ai.ragent.novel.controller.request.NovelContinueRequest;
import com.huangwei.ai.ragent.novel.dao.entity.NovelSummaryDO;
import com.huangwei.ai.ragent.novel.dao.mapper.NovelSummaryMapper;
import com.huangwei.ai.ragent.novel.service.NovelService;
import com.huangwei.ai.ragent.rag.config.RAGDefaultProperties;
import com.huangwei.ai.ragent.rag.core.memory.ConversationMemoryService;
import com.huangwei.ai.ragent.rag.core.retrieve.MilvusRetrieverService;
import com.huangwei.ai.ragent.rag.service.ConversationMessageService;
import com.huangwei.ai.ragent.rag.service.bo.ConversationMessageBO;
import com.huangwei.ai.ragent.rag.core.retrieve.RetrieveRequest;
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
public class NovelServiceImpl implements NovelService {

    private final IngestionTaskService ingestionTaskService;
    private final MilvusRetrieverService milvusRetrieverService;
    private final MilvusClientV2 milvusClient;
    private final RAGDefaultProperties ragDefaultProperties;
    private final NovelSummaryMapper novelSummaryMapper;
    private final ConversationMemoryService conversationMemoryService;
    private final ConversationMessageService conversationMessageService;
    private final LLMService llmService;

    private static final int MAX_UPLOAD_CHARS = 5000;

    @Override
    public IngestionResult upload(String pipelineId, MultipartFile file) {
        log.info("小说文件上传，pipelineId={}, fileName={}", pipelineId, file.getOriginalFilename());

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

        // 2. 入库成功后，从 Milvus 取出所有 chunk 拼接原文，提取摘要
        if (result.getStatus() != null && result.getChunkCount() != null && result.getChunkCount() > 0) {
            try {
                extractAndSaveSummary(result.getTaskId(), file.getOriginalFilename());
            } catch (Exception e) {
                log.error("小说摘要提取失败，不影响入库结果，taskId={}", result.getTaskId(), e);
            }
        }

        return result;
    }

    @Override
    public void continueNovel(NovelContinueRequest request, SseEmitter emitter) {
        // 0. 会话ID：为空则新建
        String conversationId = StrUtil.isBlank(request.getConversationId())
                ? IdUtil.getSnowflakeNextIdStr() : request.getConversationId();
        String userId = UserContext.getUserId();

        // 1. 存储用户消息（续写方向）
        String direction = StrUtil.isBlank(request.getDirection()) ? "请根据原文风格和已有剧情自然续写" : request.getDirection();
        conversationMemoryService.append(conversationId, userId, ChatMessage.user(direction));

        // 2. 加载本次会话的历史消息（之前的续写方向和结果）
        List<ChatMessage> history = conversationMemoryService.load(conversationId, userId);

        // 3. 查询小说摘要（提前加载，用于空方向时的检索 query）
        NovelSummaryDO summary = findLatestSummary();
        String summaryContext = buildSummaryContext(summary);

        // 4. 根据续写方向检索相关小说片段（方向为空时用剧情摘要检索）
        String query = request.getDirection();
        if (StrUtil.isBlank(query)) {
            query = (summary != null && StrUtil.isNotBlank(summary.getPlotSummary())) ? summary.getPlotSummary() : "小说剧情";
        }
        RetrieveRequest retrieveRequest = RetrieveRequest.builder()
                .query(query)
                .topK(request.getTopK())
                .collectionName(request.getCollectionName())
                .build();
        List<RetrievedChunk> chunks = milvusRetrieverService.retrieve(retrieveRequest);

        if (chunks.isEmpty()) {
            try {
                emitter.send(SseEmitter.event().data("未检索到相关小说内容，请确认已上传小说文件并完成入库。"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return;
        }

        // 5. 拼接检索到的相关片段
        String context = chunks.stream()
                .map(c -> "[相关度: " + String.format("%.2f", c.getScore()) + "]\n" + c.getText())
                .collect(Collectors.joining("\n\n---\n\n"));

        // 6. 组装 prompt（带历史对话上下文）
        String systemPrompt = buildSystemPrompt(request.getWordCount());
        String userPrompt = buildUserPrompt(summaryContext, context, direction);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        // 将历史续写记录作为上下文传入，让 LLM 知道之前写了什么
        for (ChatMessage historyMsg : history) {
            messages.add(historyMsg);
        }
        messages.add(ChatMessage.user(userPrompt));

        // 构建 prompt 快照，用于存储到数据库
        String promptSnapshot = buildPromptSnapshot(messages);

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .modelId(request.getModelId())
                .temperature(0.8)
                .topP(0.9)
                .build();

        // 7. 流式调用 LLM，收集完整响应用于存储
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
                // 存储 assistant 消息（含 prompt 快照）
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
                    log.error("存储续写结果失败", e);
                }
                // 返回 conversationId 给前端，方便下次续写关联同一会话
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
                log.error("小说续写 LLM 调用失败", error);
                try {
                    emitter.send(SseEmitter.event().name("error").data("续写生成失败: " + error.getMessage()));
                } catch (Exception e) {
                    // ignore
                }
                emitter.completeWithError(error);
            }
        });
    }

    // ==================== 摘要提取 ====================

    /**
     * 从 Milvus 取出该任务的所有 chunk，拼接后让 LLM 提取人物/世界观/剧情摘要，存入 DB
     */
    private void extractAndSaveSummary(String taskId, String fileName) {
        String collectionName = ragDefaultProperties.getCollectionName();

        // 从 Milvus 按 task_id 查询所有 chunk
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

        // 拼接所有 chunk 内容
        String fullText = results.stream()
                .map(r -> String.valueOf(r.getEntity().get("content")))
                .collect(Collectors.joining("\n"));

        if (StrUtil.isBlank(fullText)) {
            log.warn("拼接后的文本为空，taskId={}", taskId);
            return;
        }

        // 截取前 8000 字用于摘要提取（避免 token 过长）
        if (fullText.length() > 8000) {
            fullText = fullText.substring(0, 8000);
        }

        // 调用 LLM 提取摘要
        String characters = extractByLLM("人物", fullText);
        String worldSetting = extractByLLM("世界观设定", fullText);
        String plotSummary = extractByLLM("已有剧情线", fullText);

        // 存入数据库
        NovelSummaryDO summary = NovelSummaryDO.builder()
                .taskId(taskId)
                .fileName(fileName)
                .characters(characters)
                .worldSetting(worldSetting)
                .plotSummary(plotSummary)
                .build();
        novelSummaryMapper.insert(summary);
        log.info("小说摘要提取完成，taskId={}, fileName={}", taskId, fileName);
    }

    private String extractByLLM(String aspect, String text) {
        String prompt = """
                请从小说原文中提取【%s】的摘要信息。

                要求：
                - 用简洁的条目式列出关键信息
                - 人物：列出主要人物姓名、性格特点、喜好、处事态度、人物关系
                - 世界观：列出重要的地名、势力、规则、设定
                - 剧情线：概括已完成的主要情节、当前进展、悬念伏笔
                - 如果原文中没有相关信息，返回"暂无相关信息"
                - 不要加任何解释说明，直接输出摘要内容
                - 控制在 500 字以内

                小说原文：
                %s
                """.formatted(aspect, text);

        ChatRequest req = ChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .temperature(0.3)
                .build();
        return llmService.chat(req);
    }

    // ==================== 摘要查询 ====================

    private NovelSummaryDO findLatestSummary() {
        return novelSummaryMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NovelSummaryDO>()
                        .eq(NovelSummaryDO::getDeleted, 0)
                        .orderByDesc(NovelSummaryDO::getCreateTime)
                        .last("LIMIT 1")
        );
    }

    private String buildSummaryContext(NovelSummaryDO summary) {
        if (summary == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(summary.getCharacters())) {
            sb.append("【主要人物】\n").append(summary.getCharacters()).append("\n\n");
        }
        if (StrUtil.isNotBlank(summary.getWorldSetting())) {
            sb.append("【世界观设定】\n").append(summary.getWorldSetting()).append("\n\n");
        }
        if (StrUtil.isNotBlank(summary.getPlotSummary())) {
            sb.append("【已有剧情线】\n").append(summary.getPlotSummary()).append("\n\n");
        }
        return sb.toString();
    }

    // ==================== Prompt ====================

    private String buildSystemPrompt(int wordCount) {
        return """
                你是一位专业的小说续写作者。你的任务是根据提供的小说原文片段和设定信息，按照用户指定的方向进行续写。

                规则（按优先级从高到低）：
                1. 【最高优先级】用户的续写方向和要求是一切的出发点，必须严格遵从。如果用户要求改变风格、更换主角、调整世界观或颠覆原有设定，以用户要求为准
                2. 仅在用户未特别指定的情况下，才默认参考原文的写作风格、人物设定和世界观
                3. 情节发展要自然连贯，与已有剧情线衔接流畅
                4. 续写内容要丰富生动，包含细节描写、对话和心理活动
                5. 续写长度约 %d 字
                6. 直接输出续写内容，不要加任何解释说明
                """.formatted(wordCount);
    }

    private String buildUserPrompt(String summaryContext, String chunkContext, String direction) {
        StringBuilder sb = new StringBuilder();

        // 用户方向放在最前面，作为最高优先级指令
        sb.append("【用户续写要求】（最高优先级，必须严格遵从）\n");
        sb.append(direction);
        sb.append("\n\n----------\n\n");

        if (StrUtil.isNotBlank(summaryContext)) {
            sb.append("【原文参考信息】（以下内容仅供参考，如果与上面的用户要求冲突，以用户要求为准）\n\n");
            sb.append(summaryContext);
            sb.append("----------\n\n");
        }

        sb.append("【相关原文片段】（参考风格和语境）\n\n");
        sb.append(chunkContext);

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
