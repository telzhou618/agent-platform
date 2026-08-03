package com.example.agent.system.agent.knowledge;

import cn.hutool.core.util.StrUtil;
import com.aliyun.bailian20231229.models.RetrieveResponse;
import com.aliyun.bailian20231229.models.RetrieveResponseBody;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.integration.bailian.BailianClient;
import io.agentscope.core.rag.integration.bailian.BailianConfig;
import io.agentscope.core.rag.integration.bailian.QueryHistoryEntry;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

/**
 * 阿里云百炼知识库检索器：复用扩展包中未废弃的 BailianClient 发起检索，
 * 后处理（分数过滤、降序、截断、多轮历史转写）与原 BailianKnowledge 一致。
 * 查询改写 / 重排序由 BailianConfig 中的 enableRewrite / enableReranking 控制，
 * 在客户端内部生效。
 */
@Slf4j
public class BailianKnowledgeRetriever implements KnowledgeRetriever {

    private final BailianClient client;
    private final String indexId;
    private final double scoreThreshold;

    public BailianKnowledgeRetriever(BailianConfig config, double scoreThreshold) {
        try {
            this.client = new BailianClient(config);
        } catch (Exception e) {
            throw new IllegalStateException("创建百炼检索客户端失败：" + e.getMessage(), e);
        }
        this.indexId = config.getIndexId();
        this.scoreThreshold = scoreThreshold;
    }

    @Override
    public Mono<List<KnowledgeDocument>> retrieve(String query, int limit, List<Msg> conversationHistory) {
        if (StrUtil.isBlank(query)) {
            return Mono.just(List.of());
        }
        List<QueryHistoryEntry> queryHistory = conversationHistory == null || conversationHistory.isEmpty()
                ? null : toQueryHistory(conversationHistory);
        return client.retrieve(indexId, query, limit, queryHistory)
                .map(RetrieveResponse::getBody)
                .map(this::convert)
                .map(docs -> docs.stream()
                        .filter(doc -> doc.score() != null && doc.score() >= scoreThreshold)
                        .sorted(Comparator.comparing(KnowledgeDocument::score,
                                Comparator.reverseOrder()))
                        .limit(limit)
                        .toList())
                .doOnError(error -> log.error("百炼知识库检索失败", error));
    }

    /**
     * 响应转换：取节点文本与分数，跳过空文本节点
     */
    private List<KnowledgeDocument> convert(RetrieveResponseBody body) {
        if (body == null || body.getData() == null || body.getData().getNodes() == null) {
            return List.of();
        }
        return body.getData().getNodes().stream()
                .filter(node -> node != null && StrUtil.isNotBlank(node.getText()))
                .map(node -> new KnowledgeDocument(node.getText(), node.getScore()))
                .toList();
    }

    /**
     * 会话历史转百炼多轮改写格式：仅取 USER / ASSISTANT 消息的文本块
     */
    private List<QueryHistoryEntry> toQueryHistory(List<Msg> messages) {
        return messages.stream()
                .filter(msg -> msg.getRole() == MsgRole.USER || msg.getRole() == MsgRole.ASSISTANT)
                .map(msg -> new QueryHistoryEntry(
                        msg.getRole() == MsgRole.USER ? "user" : "assistant", extractText(msg)))
                .filter(entry -> StrUtil.isNotBlank(entry.getContent()))
                .toList();
    }

    /**
     * 拼接消息中的全部文本块，忽略图片、工具调用等非文本内容
     */
    private String extractText(Msg msg) {
        if (msg.getContent() == null || msg.getContent().isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock textBlock) {
                if (text.length() > 0) {
                    text.append("\n");
                }
                text.append(textBlock.getText());
            }
        }
        return text.toString();
    }
}
