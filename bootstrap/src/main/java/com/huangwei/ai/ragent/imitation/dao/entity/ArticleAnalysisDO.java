package com.huangwei.ai.ragent.imitation.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 文章分析实体
 * 上传参考文章时由 LLM 自动提取的核心论点、写作风格、文章结构，仿写时作为基础上下文
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_article_analysis")
public class ArticleAnalysisDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联的 ingestion task ID
     */
    private String taskId;

    /**
     * 源文件名
     */
    private String fileName;

    /**
     * 核心论点/要点摘要
     */
    private String keyPoints;

    /**
     * 写作风格分析（用词特点、句式风格、语气等）
     */
    private String writingStyle;

    /**
     * 文章结构分析（段落组织、逻辑脉络、论述层次等）
     */
    private String structure;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 修改时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Integer deleted;
}
