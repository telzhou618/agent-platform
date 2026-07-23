package com.example.agent.system.agent;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.example.agent.system.entity.AgentInfo;
import com.example.agent.system.service.AgentInfoService;
import com.example.agent.system.service.ModelConfigService;
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

import java.util.List;

/**
 * 对话服务：所有会话共用全局唯一的 ReActAgent，
 * 每次对话按智能体配置组装 {@link AgentRuntimeConfig} 放入 RuntimeContext，
 * 由 DynamicAgentMiddleware 动态加载系统提示词、模型和工具。
 * 会话历史由 AgentScope 按 (userId, sessionId) 自动维护，换新 sessionId 即新开会话。
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 用户 ID 暂时固定，将来接入登录后再替换 */
    public static final String DEFAULT_USER_ID = "default";

    private final ReActAgent reactAgent;
    private final AgentInfoService agentInfoService;
    private final ModelConfigService modelConfigService;
    private final ModelFactory modelFactory;

    /** 生成新会话 ID */
    public String newSessionId() {
        return IdUtil.simpleUUID();
    }

    /**
     * 流式对话：返回 {@link ChatChunk} 流，包含回复文本、思考过程和工具调用的增量信息。
     * agentId 为空或智能体不存在时使用全局默认提示词 + 默认模型 + 无工具。
     */
    public Flux<ChatChunk> streamChat(String sessionId, Long agentId, String text) {
        AgentInfo agent = agentId == null ? null : agentInfoService.getById(agentId);
        AgentRuntimeConfig config = new AgentRuntimeConfig(
                agent == null ? null : agent.getSysPrompt(),
                modelFactory.fromConfig(agent == null || agent.getModelId() == null
                        ? null : modelConfigService.getById(agent.getModelId())),
                agent == null ? List.of() : parseToolNames(agent.getTools()));
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(sessionId)
                .userId(DEFAULT_USER_ID)
                .put(AgentRuntimeConfig.class, config)
                .build();
        return reactAgent.streamEvents(new UserMessage(text), ctx)
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

    /** JSON 数组字符串 -> 工具名列表 */
    private List<String> parseToolNames(String toolsJson) {
        if (StrUtil.isBlank(toolsJson)) {
            return List.of();
        }
        return JSONUtil.toList(toolsJson, String.class);
    }
}
