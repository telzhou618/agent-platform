package com.example.agent.system.agent.knowledge;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.util.List;

/**
 * 知识库检索工具：注册进智能体工具箱后，模型通过 retrieve_knowledge 工具自主检索。
 * 替代 AgentScope 2.0 已废弃的 KnowledgeRetrievalTools；工具名、参数、返回格式与
 * 官方原实现逐字对齐，已配置知识库的智能体行为不变。会话历史从调用上下文提取，
 * 供支持多轮转写的知识库（百炼）提升检索准确率。
 */
public class KnowledgeRetrieveTool {

    private static final int DEFAULT_LIMIT = 5;

    private final KnowledgeRetriever retriever;

    public KnowledgeRetrieveTool(KnowledgeRetriever retriever) {
        if (retriever == null) {
            throw new IllegalArgumentException("KnowledgeRetriever 不能为空");
        }
        this.retriever = retriever;
    }

    @Tool(
            name = "retrieve_knowledge",
            description =
                    "Retrieve relevant documents from knowledge base. Use this tool when you need"
                        + " to find specific information or when user asks questions about stored"
                        + " knowledge.",
            readOnly = true,
            concurrencySafe = true)
    public String retrieveKnowledge(
            @ToolParam(
                            name = "query",
                            description =
                                    "The search query to find relevant documents in the knowledge"
                                            + " base")
                    String query,
            @ToolParam(
                            name = "limit",
                            description = "Maximum number of documents to retrieve (default: 5)",
                            required = false)
                    Integer limit,
            Agent agent,
            RuntimeContext ctx) {

        int count = limit == null ? DEFAULT_LIMIT : limit;

        // 从调用级状态提取会话历史（供百炼多轮改写使用）
        List<Msg> conversationHistory = null;
        if (agent instanceof ReActAgent) {
            var state = RuntimeContext.resolveAgentState(ctx, agent);
            if (state != null) {
                conversationHistory = state.getContext();
            }
        }

        return retriever
                .retrieve(query, count, conversationHistory)
                .map(this::formatDocuments)
                .onErrorReturn("Failed to retrieve knowledge for query: " + query)
                .block(); // 转为同步调用以匹配 Tool 接口
    }

    /**
     * 检索结果格式化为模型可读的文本（与官方原实现的输出格式一致）
     */
    private String formatDocuments(List<KnowledgeDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return "No relevant documents found in the knowledge base.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Retrieved ").append(documents.size()).append(" relevant document(s):\n\n");
        for (int i = 0; i < documents.size(); i++) {
            KnowledgeDocument doc = documents.get(i);
            sb.append("Document ").append(i + 1);
            if (doc.score() != null) {
                sb.append(" (Score: ").append(String.format("%.3f", doc.score())).append(")");
            }
            sb.append(":\n");
            sb.append(doc.content()).append("\n\n");
        }
        return sb.toString();
    }
}
