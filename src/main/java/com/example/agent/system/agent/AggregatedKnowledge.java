package com.example.agent.system.agent;

import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.RetrieveConfig;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

/**
 * 多知识库聚合检索：与 ReActAgent AGENTIC 模式内部的聚合逻辑等价。
 * retrieve 扇出到全部知识库，合并后按分数降序截断；单个知识库失败只跳过不报错。
 */
public class AggregatedKnowledge implements Knowledge {

    private final List<Knowledge> knowledgeBases;

    public AggregatedKnowledge(List<Knowledge> knowledgeBases) {
        this.knowledgeBases = List.copyOf(knowledgeBases);
    }

    @Override
    public Mono<List<Document>> retrieve(String query, RetrieveConfig config) {
        return Flux.fromIterable(knowledgeBases)
                .flatMap(kb -> kb.retrieve(query, config).onErrorResume(e -> Mono.just(List.of())))
                .collectList()
                .map(lists -> {
                    var merged = lists.stream().flatMap(List::stream)
                            .sorted(Comparator.comparing(Document::getScore,
                                    Comparator.nullsLast(Comparator.reverseOrder())))
                            .toList();
                    int limit = config != null ? config.getLimit() : 0;
                    return limit > 0 && merged.size() > limit ? merged.subList(0, limit) : merged;
                });
    }

    /** 平台的知识库均为外部只读来源（百炼/Dify），不支持写入 */
    @Override
    public Mono<Void> addDocuments(List<Document> documents) {
        return Mono.empty();
    }
}
