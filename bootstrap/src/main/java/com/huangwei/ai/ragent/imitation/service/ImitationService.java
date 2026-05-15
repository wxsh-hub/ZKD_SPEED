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

package com.huangwei.ai.ragent.imitation.service;

import com.huangwei.ai.ragent.imitation.controller.request.ArticleRewriteRequest;
import com.huangwei.ai.ragent.ingestion.domain.result.IngestionResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 文章仿写服务
 */
public interface ImitationService {

    /**
     * 上传参考文章，走 ingestion pipeline 解析、分块、向量化后存入 Milvus
     *
     * @param pipelineId 流水线ID
     * @param file       上传的文章文件
     * @return 摄入结果
     */
    IngestionResult upload(String pipelineId, MultipartFile file);

    /**
     * 根据参考文章进行仿写（SSE 流式输出）
     * <p>
     * 流程：检索相关片段 -> 组装仿写 prompt -> LLM 流式生成
     *
     * @param request 仿写请求
     * @param emitter SSE 发射器
     */
    void rewrite(ArticleRewriteRequest request, SseEmitter emitter);
}
