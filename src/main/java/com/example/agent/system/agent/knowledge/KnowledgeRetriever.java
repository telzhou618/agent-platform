package com.example.agent.system.agent.knowledge;

import io.agentscope.core.message.Msg;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 知识库检索器：平台自有的知识库抽象，替代 AgentScope 2.0 已废弃的
 * io.agentscope.core.rag.Knowledge 接口。底层实现直接复用扩展包中未废弃的
 * BailianClient / DifyRAGClient，官方新 RAG 模块落地后只需替换实现层。
 */
public interface KnowledgeRetriever {

    /**
     * 按查询检索相关文档
     *
     * @param query               查询文本
     * @param limit               最大返回条数
     * @param conversationHistory 会话历史（支持多轮转写的来源如百炼可用于提升准确率），可为 null
     * @return 命中文档列表（按相关度排序）
     */
    Mono<List<KnowledgeDocument>> retrieve(String query, int limit, List<Msg> conversationHistory);
}
