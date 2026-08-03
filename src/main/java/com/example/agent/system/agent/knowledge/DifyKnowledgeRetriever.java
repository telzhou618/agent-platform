package com.example.agent.system.agent.knowledge;

import cn.hutool.core.util.StrUtil;
import io.agentscope.core.message.Msg;
import io.agentscope.core.rag.integration.dify.DifyRAGClient;
import io.agentscope.core.rag.integration.dify.DifyRAGConfig;
import io.agentscope.core.rag.integration.dify.model.DifyResponse;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

/**
 * Dify 知识库检索器：复用扩展包中未废弃的 DifyRAGClient 发起检索。
 * topK / scoreThreshold / 重排序通过 DifyRAGConfig 在 API 请求层面生效，
 * 此处再做一次客户端分数过滤与排序截断，行为与原 DifyKnowledge 对齐。
 */
@Slf4j
public class DifyKnowledgeRetriever implements KnowledgeRetriever {

    private final DifyRAGClient client;
    private final double scoreThreshold;

    public DifyKnowledgeRetriever(DifyRAGConfig config, double scoreThreshold) {
        this.client = new DifyRAGClient(config);
        this.scoreThreshold = scoreThreshold;
    }

    @Override
    public Mono<List<KnowledgeDocument>> retrieve(String query, int limit, List<Msg> conversationHistory) {
        if (StrUtil.isBlank(query)) {
            return Mono.just(List.of());
        }
        return client.retrieve(query, limit)
                .map(this::convert)
                .map(docs -> docs.stream()
                        .filter(doc -> doc.score() == null || doc.score() >= scoreThreshold)
                        .sorted(Comparator.comparing(KnowledgeDocument::score,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(limit)
                        .toList())
                .doOnError(error -> log.error("Dify 知识库检索失败", error));
    }

    /**
     * 响应转换：取分段内容与分数，跳过空分段
     */
    private List<KnowledgeDocument> convert(DifyResponse response) {
        if (response == null || response.getRecords() == null) {
            return List.of();
        }
        return response.getRecords().stream()
                .filter(record -> record != null && record.getSegment() != null
                        && StrUtil.isNotBlank(record.getSegment().getContent()))
                .map(record -> new KnowledgeDocument(record.getSegment().getContent(), record.getScore()))
                .toList();
    }
}
