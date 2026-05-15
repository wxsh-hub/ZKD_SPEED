package com.huangwei.ai.ragent.imitation.controller;

import com.huangwei.ai.ragent.framework.convention.Result;
import com.huangwei.ai.ragent.framework.web.Results;
import com.huangwei.ai.ragent.imitation.controller.request.ArticleRewriteRequest;
import com.huangwei.ai.ragent.imitation.service.ImitationService;
import com.huangwei.ai.ragent.ingestion.domain.result.IngestionResult;
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
 * 文章仿写控制器
 * <p>
 * 提供两个核心接口：
 * 1. 上传参考文章 -> 解析、分块、向量化入库，同时提取文章分析摘要
 * 2. 指定改写要求 -> 检索相关片段 -> LLM 流式仿写（同义替换、语序调整，保持逻辑结构）
 */
@RestController
@RequiredArgsConstructor
public class ImitationController {

    private final ImitationService imitationService;

    /**
     * 上传参考文章并入库
     * <p>
     * 复用现有的 ingestion pipeline：解析 -> 分块 -> 向量化 -> 写入 Milvus
     *
     * @param pipelineId 流水线ID
     * @param file       参考文章文件（支持 txt、md、pdf、docx 等）
     * @return 摄入结果（任务ID、状态、分块数量）
     */
    @PostMapping(value = "/imitation/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<IngestionResult> upload(@RequestParam("pipelineId") String pipelineId,
                                          @RequestPart("file") MultipartFile file) {
        return Results.success(imitationService.upload(pipelineId, file));
    }

    /**
     * 文章仿写（SSE 流式输出）
     * <p>
     * 流程：检索参考文章片段 -> 组装仿写 prompt -> LLM 流式生成
     * 通过同义词替换、语序调整等方法改写，保持原文逻辑和行文结构不变
     *
     * @param request 仿写请求（附加要求、字数、集合名、topK）
     * @return SSE 流式事件流
     */
    @PostMapping(value = "/imitation/rewrite", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter rewrite(@RequestBody @Valid ArticleRewriteRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        imitationService.rewrite(request, emitter);
        return emitter;
    }
}
