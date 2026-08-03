package com.example.agent.system.agent.knowledge;

import io.agentscope.core.message.Msg;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

/**
 * 多知识库聚合检索：扇出到全部检索器，合并后按分数降序截断；
 * 单个知识库失败只跳过不报错。逻辑与原 AggregatedKnowledge 等价。
 */
public class AggregatedKnowledgeRetriever implements KnowledgeRetriever {

    private final List<KnowledgeRetriever> retrievers;

    public AggregatedKnowledgeRetriever(List<KnowledgeRetriever> retrievers) {
        this.retrievers = List.copyOf(retrievers);
    }

    @Override
    public Mono<List<KnowledgeDocument>> retrieve(String query, int limit, List<Msg> conversationHistory) {
        return Flux.fromIterable(retrievers)
                .flatMap(retriever -> retriever.retrieve(query, limit, conversationHistory)
                        .onErrorResume(e -> Mono.just(List.of())))
                .collectList()
                .map(lists -> {
                    var merged = lists.stream().flatMap(List::stream)
                            .sorted(Comparator.comparing(KnowledgeDocument::score,
                                    Comparator.nullsLast(Comparator.reverseOrder())))
                            .toList();
                    return limit > 0 && merged.size() > limit ? merged.subList(0, limit) : merged;
                });
    }
}
