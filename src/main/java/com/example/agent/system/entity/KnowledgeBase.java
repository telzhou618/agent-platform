package com.example.agent.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 知识库（RAG 检索） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_base")
public class KnowledgeBase extends BaseEntity {

    /** 类型：阿里云百炼 */
    public static final String TYPE_BAILIAN = "bailian";
    /** 类型：Dify */
    public static final String TYPE_DIFY = "dify";

    /** 知识库名称 */
    private String name;

    /** 类型：bailian/dify，未来可扩展 */
    private String type;

    /** 类型相关配置，JSON 对象 */
    private String config;

    /** 默认检索条数 */
    private Integer retrieveLimit;

    /** 默认分数阈值 */
    private Double scoreThreshold;

    /** 备注 */
    private String remark;
}
