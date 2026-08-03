package com.example.agent.system.agent.knowledge;

/**
 * 知识库检索结果：一条命中文档的内容与相关度分数。
 * 平台自有模型，替代 AgentScope 2.0 已废弃的 io.agentscope.core.rag.model.Document。
 *
 * @param content 文档片段文本
 * @param score   相关度分数（部分来源可能不返回，为 null）
 */
public record KnowledgeDocument(String content, Double score) {

}
