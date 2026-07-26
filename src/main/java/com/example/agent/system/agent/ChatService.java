package com.example.agent.system.agent;

import cn.hutool.core.util.IdUtil;
import com.example.agent.system.auth.LoginHelper;
import com.example.agent.system.service.ChatRecordService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.tool.mcp.McpMeta;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对话服务：按 agentId 从 Spring 容器取对应的 HarnessAgent 实例进行对话，
 * 实例的系统提示词、模型、工具、知识库、技能仓库在注册时已固化（见 AgentRegistry）；
 * 取不到实例时回退全局默认智能体。
 * 会话历史由 AgentScope 按 (userId, sessionId) 自动维护，换新 sessionId 即新开会话。
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 无登录上下文（如单元测试）时的兜底用户 ID */
    private static final String FALLBACK_USER_ID = "default";

    private final HarnessAgent defaultAgent;
    private final AgentRegistry agentRegistry;
    private final ChatRecordService chatRecordService;

    /** 生成新会话 ID */
    public String newSessionId() {
        return IdUtil.simpleUUID();
    }

    /** 当前登录用户 ID（字符串形式），无登录上下文时回退 default */
    private static String currentUserId() {
        Long id = LoginHelper.currentUserId();
        return id == null ? FALLBACK_USER_ID : String.valueOf(id);
    }

    /** 流式对话：返回 {@link ChatChunk} 流，包含回复文本、思考过程和工具调用的增量信息 */
    public Flux<ChatChunk> streamChat(String sessionId, Long agentId, String text) {
        HarnessAgent agent = agentId == null ? null : agentRegistry.find(agentId);
        if (agent == null) {
            agent = defaultAgent;
        }

        // mcp 元数据, 会自动传递给下游的 MCP 服务
        McpMeta meta = new McpMeta(Map.of(
                "userId", currentUserId(),
                "sessionId", sessionId,
                "traceId", IdUtil.fastSimpleUUID()
        ));

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(sessionId)
                .userId(currentUserId())
                .put(McpMeta.class, meta)
                .build();
        // 平台工具均由管理员显式配置，不做人工确认：BYPASS 跳过权限评估。
        // AgentScope 默认对非只读 MCP 工具逐次要求用户授权（HITL），
        // 不设置会导致工具调用挂起、后续对话报 "Agent is paused" 错误。
        agent.setPermissionMode(ctx, PermissionMode.BYPASS);
        Flux<ChatChunk> chunks = agent.streamEvents(new UserMessage(text), ctx)
                .flatMap(e -> Mono.justOrEmpty(toChunk(e)));
        // 默认智能体（agentId 为空）不属于任何配置，不做统计
        if (agentId == null) {
            return chunks;
        }
        return record(sessionId, agentId, chunks);
    }

    /** 埋点：统计工具调用次数和耗时，流结束时异步落一条对话记录 */
    private Flux<ChatChunk> record(String sessionId, Long agentId, Flux<ChatChunk> chunks) {
        long startMillis = System.currentTimeMillis();
        AtomicInteger toolCalls = new AtomicInteger();
        AtomicBoolean failed = new AtomicBoolean();
        return chunks
                .doOnNext(c -> {
                    if (c.kind() == ChatChunk.Kind.TOOL_CALL_START) {
                        toolCalls.incrementAndGet();
                    }
                })
                .doOnError(e -> failed.set(true))
                .doFinally(signal -> chatRecordService.record(agentId, sessionId, toolCalls.get(),
                        System.currentTimeMillis() - startMillis, !failed.get()));
    }

    /** AgentScope 事件 -> 对话输出单元；不关心的事件返回 null（被过滤掉） */
    private ChatChunk toChunk(AgentEvent e) {
        if (e instanceof TextBlockDeltaEvent t) {
            return ChatChunk.of(ChatChunk.Kind.TEXT, t.getDelta());
        }
        if (e instanceof ThinkingBlockDeltaEvent t) {
            return ChatChunk.of(ChatChunk.Kind.THINKING, t.getDelta());
        }
        if (e instanceof ToolCallStartEvent t) {
            return ChatChunk.tool(ChatChunk.Kind.TOOL_CALL_START, t.getToolCallName(), null);
        }
        if (e instanceof ToolCallDeltaEvent t) {
            return ChatChunk.tool(ChatChunk.Kind.TOOL_CALL_ARGS, t.getToolCallName(), t.getDelta());
        }
        if (e instanceof ToolResultTextDeltaEvent t) {
            return ChatChunk.tool(ChatChunk.Kind.TOOL_RESULT, t.getToolCallName(), t.getDelta());
        }
        if (e instanceof ToolResultEndEvent t) {
            return ChatChunk.tool(ChatChunk.Kind.TOOL_CALL_END, t.getToolCallName(),
                    t.getState().getValue());
        }
        return null;
    }
}
