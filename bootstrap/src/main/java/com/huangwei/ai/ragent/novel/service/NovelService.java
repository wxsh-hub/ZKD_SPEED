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

package com.huangwei.ai.ragent.novel.service;

import com.huangwei.ai.ragent.ingestion.domain.result.IngestionResult;
import com.huangwei.ai.ragent.novel.controller.request.NovelContinueRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 小说续写服务
 */
public interface NovelService {

    /**
     * 上传小说文件，走 ingestion pipeline 解析、分块、向量化后存入 Milvus
     *
     * @param pipelineId 流水线ID
     * @param file       上传的小说文件
     * @return 摄入结果
     */
    IngestionResult upload(String pipelineId, MultipartFile file);

    /**
     * 根据续写方向检索相关 chunk，组装 prompt 让 LLM 流式生成续写内容
     *
     * @param request 续写请求（方向、集合名、topK）
     * @param emitter SSE 发射器
     */
    void continueNovel(NovelContinueRequest request, SseEmitter emitter);
}
