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

package com.huangwei.ai.ragent.rag.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.huangwei.ai.ragent.rag.config.MemoryProperties;
import com.huangwei.ai.ragent.rag.controller.request.ConversationCreateRequest;
import com.huangwei.ai.ragent.rag.controller.request.ConversationUpdateRequest;
import com.huangwei.ai.ragent.rag.controller.vo.ConversationVO;
import com.huangwei.ai.ragent.rag.dao.entity.ConversationDO;
import com.huangwei.ai.ragent.rag.dao.entity.ConversationMessageDO;
import com.huangwei.ai.ragent.rag.dao.entity.ConversationSummaryDO;
import com.huangwei.ai.ragent.rag.dao.mapper.ConversationMapper;
import com.huangwei.ai.ragent.rag.dao.mapper.ConversationMessageMapper;
import com.huangwei.ai.ragent.rag.dao.mapper.ConversationSummaryMapper;
import com.huangwei.ai.ragent.framework.context.UserContext;
import com.huangwei.ai.ragent.framework.convention.ChatMessage;
import com.huangwei.ai.ragent.framework.convention.ChatRequest;
import com.huangwei.ai.ragent.framework.exception.ClientException;
import com.huangwei.ai.ragent.infra.chat.LLMService;
import com.huangwei.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import com.huangwei.ai.ragent.rag.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.huangwei.ai.ragent.rag.constant.RAGConstant.CONVERSATION_TITLE_PROMPT_PATH;

/**
 * 会话服务实现类
 * 处理会话的创建、更新、重命名和删除等业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final ConversationSummaryMapper summaryMapper;
    private final MemoryProperties memoryProperties;
    private final PromptTemplateLoader promptTemplateLoader;
    private final LLMService llmService;

    @Override
    public List<ConversationVO> listByUserId(String userId) {
        if (StrUtil.isBlank(userId)) {
            return List.of();
        }

        List<ConversationDO> records = conversationMapper.selectList(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getUserId, userId)
                        .eq(ConversationDO::getDeleted, 0)
                        .orderByDesc(ConversationDO::getLastTime)
        );
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        return records.stream()
                .map(item -> ConversationVO.builder()
                        .conversationId(item.getConversationId())
                        .title(item.getTitle())
                        .parentId(item.getParentId())
                        .branchFromMessageId(item.getBranchFromMessageId())
                        .lastTime(item.getLastTime())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void createOrUpdate(ConversationCreateRequest request) {
        String userId = request.getUserId();
        String conversationId = request.getConversationId();
        String question = request.getQuestion();
        if (StrUtil.isBlank(userId)) {
            throw new ClientException("用户信息缺失");
        }

        ConversationDO existing = conversationMapper.selectOne(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getConversationId, conversationId)
                        .eq(ConversationDO::getUserId, userId)
                        .eq(ConversationDO::getDeleted, 0)
        );

        if (existing == null) {
            String title = generateTitleFromQuestion(question);
            ConversationDO record = ConversationDO.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .title(title)
                    .lastTime(request.getLastTime())
                    .build();
            conversationMapper.insert(record);
            return;
        }

        existing.setLastTime(request.getLastTime());
        conversationMapper.updateById(existing);
    }

    @Override
    public void rename(String conversationId, ConversationUpdateRequest request) {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId)) {
            throw new ClientException("会话信息缺失");
        }

        String title = request.getTitle();
        if (StrUtil.isBlank(title)) {
            throw new ClientException("会话名称不能为空");
        }
        int maxLen = memoryProperties.getTitleMaxLength();
        if (title.length() > maxLen) {
            throw new ClientException("会话名称长度不能超过" + maxLen + "个字符");
        }

        ConversationDO record = conversationMapper.selectOne(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getConversationId, conversationId)
                        .eq(ConversationDO::getUserId, userId)
                        .eq(ConversationDO::getDeleted, 0)
        );
        if (record == null) {
            throw new ClientException("会话不存在");
        }

        record.setTitle(title.trim());
        conversationMapper.updateById(record);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(String conversationId) {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId)) {
            throw new ClientException("会话信息缺失");
        }

        ConversationDO record = conversationMapper.selectOne(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getConversationId, conversationId)
                        .eq(ConversationDO::getUserId, userId)
                        .eq(ConversationDO::getDeleted, 0)
        );
        if (record == null) {
            throw new ClientException("会话不存在");
        }

        // 收集所有后代会话ID（含自身）
        List<String> allIds = new ArrayList<>();
        collectDescendantIds(conversationId, userId, allIds);
        allIds.add(conversationId);

        // 批量删除所有会话及其消息和摘要
        for (String id : allIds) {
            conversationMapper.delete(
                    Wrappers.lambdaQuery(ConversationDO.class)
                            .eq(ConversationDO::getConversationId, id)
                            .eq(ConversationDO::getUserId, userId)
                            .eq(ConversationDO::getDeleted, 0)
            );
            messageMapper.delete(
                    Wrappers.lambdaQuery(ConversationMessageDO.class)
                            .eq(ConversationMessageDO::getConversationId, id)
                            .eq(ConversationMessageDO::getUserId, userId)
                            .eq(ConversationMessageDO::getDeleted, 0)
            );
            summaryMapper.delete(
                    Wrappers.lambdaQuery(ConversationSummaryDO.class)
                            .eq(ConversationSummaryDO::getConversationId, id)
                            .eq(ConversationSummaryDO::getUserId, userId)
                            .eq(ConversationSummaryDO::getDeleted, 0)
            );
        }
    }

    private void collectDescendantIds(String parentId, String userId, List<String> result) {
        List<ConversationDO> children = conversationMapper.selectList(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getParentId, parentId)
                        .eq(ConversationDO::getUserId, userId)
                        .eq(ConversationDO::getDeleted, 0)
        );
        for (ConversationDO child : children) {
            result.add(child.getConversationId());
            collectDescendantIds(child.getConversationId(), userId, result);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ConversationVO fork(String sourceConversationId, String messageId, String userId) {
        if (StrUtil.isBlank(sourceConversationId) || StrUtil.isBlank(messageId) || StrUtil.isBlank(userId)) {
            throw new ClientException("分叉参数缺失");
        }
        Long messageIdLong;
        try {
            messageIdLong = Long.parseLong(messageId);
        } catch (NumberFormatException e) {
            throw new ClientException("消息ID格式错误");
        }

        // 验证源会话存在且属于当前用户
        ConversationDO sourceConversation = conversationMapper.selectOne(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getConversationId, sourceConversationId)
                        .eq(ConversationDO::getUserId, userId)
                        .eq(ConversationDO::getDeleted, 0)
        );
        if (sourceConversation == null) {
            throw new ClientException("源会话不存在");
        }

        // 获取指定消息及之前的所有消息
        List<ConversationMessageDO> sourceMessages = messageMapper.selectList(
                Wrappers.lambdaQuery(ConversationMessageDO.class)
                        .eq(ConversationMessageDO::getConversationId, sourceConversationId)
                        .eq(ConversationMessageDO::getUserId, userId)
                        .eq(ConversationMessageDO::getDeleted, 0)
                        .le(ConversationMessageDO::getId, messageIdLong)
                        .orderByAsc(ConversationMessageDO::getId)
        );

        if (sourceMessages.isEmpty()) {
            throw new ClientException("未找到指定消息");
        }

        // 用用户的提问内容生成标题（从后往前找最后一条 user 消息）
        String userQuestion = null;
        for (int i = sourceMessages.size() - 1; i >= 0; i--) {
            if ("user".equals(sourceMessages.get(i).getRole())) {
                userQuestion = sourceMessages.get(i).getContent();
                break;
            }
        }
        if (userQuestion == null) {
            userQuestion = sourceMessages.get(sourceMessages.size() - 1).getContent();
        }
        String branchTitle = generateBranchTitle(userQuestion);

        // 创建新会话
        String newConversationId = IdUtil.fastSimpleUUID();
        Date now = new Date();
        ConversationDO newConversation = ConversationDO.builder()
                .conversationId(newConversationId)
                .userId(userId)
                .title(branchTitle)
                .parentId(sourceConversationId)
                .branchFromMessageId(messageIdLong)
                .lastTime(now)
                .build();
        conversationMapper.insert(newConversation);

        // 复制消息到新会话
        List<ConversationMessageDO> newMessages = new ArrayList<>();
        for (ConversationMessageDO sourceMessage : sourceMessages) {
            ConversationMessageDO newMessage = ConversationMessageDO.builder()
                    .conversationId(newConversationId)
                    .userId(userId)
                    .role(sourceMessage.getRole())
                    .content(sourceMessage.getContent())
                    .promptSnapshot(sourceMessage.getPromptSnapshot())
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build();
            newMessages.add(newMessage);
        }

        // 批量插入新消息
        for (ConversationMessageDO newMessage : newMessages) {
            messageMapper.insert(newMessage);
        }

        log.info("会话分叉成功: source={}, new={}, messageCount={}", sourceConversationId, newConversationId, newMessages.size());

        return ConversationVO.builder()
                .conversationId(newConversationId)
                .title(newConversation.getTitle())
                .parentId(sourceConversationId)
                .branchFromMessageId(messageIdLong)
                .lastTime(now)
                .build();
    }

    private String generateTitleFromQuestion(String question) {
        int maxLen = memoryProperties.getTitleMaxLength();
        if (maxLen <= 0) {
            maxLen = 30;
        }
        String prompt = promptTemplateLoader.render(
                CONVERSATION_TITLE_PROMPT_PATH,
                Map.of(
                        "title_max_chars", String.valueOf(maxLen),
                        "question", question
                )
        );

        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(ChatMessage.user(prompt)))
                    .temperature(0.7D)
                    .topP(0.3D)
                    .thinking(false)
                    .build();

            return llmService.chat(request);
        } catch (Exception ex) {
            log.warn("生成会话标题失败", ex);
            return "新对话";
        }
    }

    private String generateBranchTitle(String messageContent) {
        String truncated = messageContent.length() > 200
                ? messageContent.substring(0, 200)
                : messageContent;
        try {
            return generateTitleFromQuestion(truncated);
        } catch (Exception e) {
            log.warn("生成分支标题失败", e);
            return "分支对话";
        }
    }
}
