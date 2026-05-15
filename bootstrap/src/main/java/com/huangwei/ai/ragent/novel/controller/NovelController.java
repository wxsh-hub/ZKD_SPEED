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

package com.huangwei.ai.ragent.novel.controller;

import com.huangwei.ai.ragent.framework.convention.Result;
import com.huangwei.ai.ragent.framework.web.Results;
import com.huangwei.ai.ragent.ingestion.domain.result.IngestionResult;
import com.huangwei.ai.ragent.novel.controller.request.NovelContinueRequest;
import com.huangwei.ai.ragent.novel.service.NovelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 小说续写控制器
 * <p>
 * 提供两个核心接口：
 * 1. 上传小说文件 -> 解析、分块、向量化入库
 * 2. 指定续写方向 -> 检索相关片段 -> LLM 流式续写
 */
@RestController
@RequiredArgsConstructor
public class NovelController {

    private final NovelService novelService;

    /**
     * 上传小说文件并入库
     * <p>
     * 复用现有的 ingestion pipeline：解析 -> 分块 -> 向量化 -> 写入 Milvus
     *
     * @param pipelineId 流水线ID（决定分块策略等配置）
     * @param file       小说文件（支持 txt、md、pdf、docx 等）
     * @return 摄入结果（任务ID、状态、分块数量）
     */
    @PostMapping(value = "/novel/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<IngestionResult> upload(@RequestParam("pipelineId") String pipelineId,
                                          @RequestPart("file") MultipartFile file) {
        return Results.success(novelService.upload(pipelineId, file));
    }

    /**
     * 小说续写（SSE 流式输出）
     * <p>
     * 流程：续写方向 -> 向量检索相关片段 -> 组装 prompt -> LLM 流式生成
     *
     * @param request 续写请求（方向、集合名、topK）
     * @return SSE 流式事件流
     */
    @PostMapping(value = "/novel/continue", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter continueNovel(@RequestBody @Valid NovelContinueRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        novelService.continueNovel(request, emitter);
        return emitter;
    }
}
