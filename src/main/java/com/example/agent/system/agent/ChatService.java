package com.example.agent.system.agent;

import cn.hutool.core.util.IdUtil;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.UserMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 对话服务：按 agentId 从 Spring 容器取对应的 ReActAgent 实例进行对话，
 * 实例的系统提示词、模型、工具在注册时已固化（见 AgentRegistry）；
 * 取不到实例时回退全局默认智能体。
 * 会话历史由 AgentScope 按 (userId, sessionId) 自动维护，换新 sessionId 即新开会话。
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 用户 ID 暂时固定，将来接入登录后再替换 */
    public static final String DEFAULT_USER_ID = "default";

    private final ReActAgent defaultAgent;
    private final AgentRegistry agentRegistry;

    /** 生成新会话 ID */
    public String newSessionId() {
        return IdUtil.simpleUUID();
    }

    /** 流式对话：返回 {@link ChatChunk} 流，包含回复文本、思考过程和工具调用的增量信息 */
    public Flux<ChatChunk> streamChat(String sessionId, Long agentId, String text) {
        ReActAgent agent = agentId == null ? null : agentRegistry.find(agentId);
        if (agent == null) {
            agent = defaultAgent;
        }
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(sessionId)
                .userId(DEFAULT_USER_ID)
                .build();
        return agent.streamEvents(new UserMessage(text), ctx)
                .flatMap(e -> Mono.justOrEmpty(toChunk(e)));
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
