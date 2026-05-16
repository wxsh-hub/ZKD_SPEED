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

package com.huangwei.ai.ragent.novel.controller.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 小说续写请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NovelContinueRequest {

    /**
     * 续写方向/要求，例如："主角进入密林后遭遇了一场暴风雨"
     * 为空时按剧情自然续写
     */
    private String direction;

    /**
     * 会话ID，为空时自动创建新会话
     * 传入已有 conversationId 可在同一次续写会话中累加历史
     */
    private String conversationId;

    /**
     * 向量集合名称，为空时使用默认集合
     */
    private String collectionName;

    /**
     * 检索的 chunk 数量，默认 10
     */
    @Builder.Default
    private int topK = 10;

    /**
     * 续写字数档位，可选值：
     * 300  - 短篇（约300字）
     * 500  - 标准（约500字）
     * 700  - 中等（约700字）
     * 1000 - 长篇（约1000字）
     * 3000 - 超长篇（约3000字）
     * 默认 700
     */
    @Builder.Default
    private int wordCount = 700;

    /**
     * 指定使用的模型ID，为空时使用默认模型
     */
    private String modelId;
}
