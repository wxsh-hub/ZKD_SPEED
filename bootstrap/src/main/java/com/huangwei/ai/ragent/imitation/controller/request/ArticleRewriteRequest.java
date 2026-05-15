package com.huangwei.ai.ragent.imitation.controller.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章仿写请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArticleRewriteRequest {

    /**
     * 附加要求/指令，例如："语气更正式一些"、"适当精简篇幅"
     * 为空时按默认策略改写
     */
    private String requirements;

    /**
     * 会话ID，为空时自动创建新会话
     * 传入已有 conversationId 可在同一会话中累加历史（多次改写调整）
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
     * 改写字数档位，可选值：
     * 300  - 精简版（约300字）
     * 500  - 简短版（约500字）
     * 800  - 标准版（约800字）
     * 1500 - 详细版（约1500字）
     * 3000 - 完整版（约3000字）
     * 默认 800
     */
    @Builder.Default
    private int wordCount = 800;

    /**
     * 指定使用的模型ID，为空时使用默认模型
     */
    private String modelId;
}
