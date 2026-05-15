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

package com.huangwei.ai.ragent.rag.service;

import com.huangwei.ai.ragent.rag.controller.request.ConversationCreateRequest;
import com.huangwei.ai.ragent.rag.controller.request.ConversationUpdateRequest;
import com.huangwei.ai.ragent.rag.controller.vo.ConversationVO;

import java.util.List;

/**
 * 会话服务接口
 * 提供会话的创建、重命名和删除功能
 */
public interface ConversationService {

    /**
     * 根据用户ID获取会话列表
     *
     * @param userId 用户ID
     * @return 会话视图对象列表
     */
    List<ConversationVO> listByUserId(String userId);

    /**
     * 创建或更新会话
     * 如果 ConversationCreateRequest 里的会话 ID 存在则更新，不存在则创建
     *
     * @param request 创建请求对象
     */
    void createOrUpdate(ConversationCreateRequest request);

    /**
     * 重命名会话
     *
     * @param conversationId 会话 ID
     * @param request        更新请求对象
     */
    void rename(String conversationId, ConversationUpdateRequest request);

    /**
     * 删除会话
     *
     * @param conversationId 会话 ID
     */
    void delete(String conversationId);

    /**
     * 从指定消息处分叉会话
     * 复制该消息及之前的所有历史消息到新会话
     *
     * @param sourceConversationId 源会话ID
     * @param messageId            消息ID（包含该消息及之前的消息）
     * @param userId               用户ID
     * @return 新会话视图对象
     */
    ConversationVO fork(String sourceConversationId, String messageId, String userId);
}
