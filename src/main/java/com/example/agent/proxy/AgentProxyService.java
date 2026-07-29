package com.example.agent.proxy;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.example.agent.proxy.dto.AgentSseEvent;
import com.example.agent.proxy.dto.AgentSession;
import com.example.agent.proxy.dto.ChatRequest;
import com.example.agent.system.agent.AgentRegistry;
import com.example.agent.system.entity.AgentInfo;
import com.example.agent.system.entity.ApiKey;
import com.example.agent.system.entity.SysUser;
import com.example.agent.system.service.AgentInfoService;
import com.example.agent.system.service.ApiKeyService;
import com.example.agent.system.service.ChatRecordService;
import com.example.agent.system.service.SysUserService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.mcp.McpMeta;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 智能体代理开放接口服务：apiKey 鉴权 + 按 key 归属用户校验智能体访问权限 + 会话管理。
 * 这些接口不经过管理端登录态（sa-token），租户拦截器在无登录上下文时自动放行，
 * 因此 apiKey -> 用户 -> 智能体的归属校验必须在这里显式完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentProxyService {

    /** 会话状态在状态存储中的 key（AgentScope 固定值） */
    private static final String AGENT_STATE_KEY = "agent_state";
    /** 无摘要时取首条用户消息截断的长度 */
    private static final int SUMMARY_MAX_LENGTH = 50;

    private final AgentRegistry agentRegistry;
    private final ChatRecordService chatRecordService;
    private final ApiKeyService apiKeyService;
    private final AgentInfoService agentInfoService;
    private final SysUserService sysUserService;
    private final SensitiveWordFilter sensitiveWordFilter;

    /**
     * 流式对话：鉴权后直接驱动 HarnessAgent，返回 {@link AgentSseEvent} 事件流。
     * 独立于管理端 ChatService（那是管理端/测试专用）；对话记录埋点与管理端同一口径。
     */
    public Flux<AgentSseEvent> streamChat(String apiKey, ChatRequest request) {
        if (request == null || request.getAgentId() == null) {
            throw new AgentProxyException("agentId 不能为空");
        }
        if (StrUtil.isBlank(request.getUserId()) || StrUtil.isBlank(request.getSessionId())
                || StrUtil.isBlank(request.getMessage())) {
            throw new AgentProxyException("userId、sessionId、message 不能为空");
        }
        AgentInfo agent = authenticate(apiKey, request.getAgentId());
        if (!agent.isEnabled()) {
            throw new AgentProxyException("智能体已禁用");
        }
        // 敏感词拦截：入参消息先过词库，命中直接拒绝
        sensitiveWordFilter.check(request.getMessage());
        HarnessAgent harnessAgent = agentRegistry.find(agent.getId());
        if (harnessAgent == null) {
            throw new AgentProxyException("智能体实例未注册（可能已删除或重建中）");
        }
        // mcp 元数据, 会自动传递给下游的 MCP 服务
        McpMeta meta = new McpMeta(Map.of(
                "userId", request.getUserId(),
                "sessionId", request.getSessionId(),
                "traceId", IdUtil.fastSimpleUUID()
        ));
        RuntimeContext context = RuntimeContext.builder()
                .sessionId(request.getSessionId())
                .userId(request.getUserId())
                .put(McpMeta.class, meta)
                .build();
        // 平台工具均由管理员显式配置，不做人工确认：BYPASS 跳过权限评估。
        // AgentScope 默认对非只读 MCP 工具逐次要求用户授权（HITL），
        // 不设置会导致工具调用挂起、后续对话报 "Agent is paused" 错误。
        harnessAgent.setPermissionMode(context, PermissionMode.BYPASS);
        // 每个请求独立的工具参数累积器
        SseConverter converter = new SseConverter();
        Flux<AgentSseEvent> events = harnessAgent.streamEvents(new UserMessage(request.getMessage()), context)
                .flatMap(event -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Agent 流事件：{}", JSON.toJSONString(event));
                    }
                    AgentSseEvent sseEvent = converter.convert(event);
                    return sseEvent == null ? Flux.empty() : Flux.just(sseEvent);
                });
        return record(request.getSessionId(), agent.getId(), events);
    }

    /**
     * 埋点：统计工具调用次数和耗时，流结束时异步落一条对话记录
     */
    private Flux<AgentSseEvent> record(String sessionId, Long agentId, Flux<AgentSseEvent> events) {
        long startMillis = System.currentTimeMillis();
        AtomicInteger toolCalls = new AtomicInteger();
        AtomicBoolean failed = new AtomicBoolean();
        return events
                .doOnNext(e -> {
                    if (SseConverter.TYPE_TOOL_CALL.equals(e.getType())) {
                        toolCalls.incrementAndGet();
                    }
                })
                .doOnError(e -> failed.set(true))
                .doFinally(signal -> chatRecordService.record(agentId, sessionId, toolCalls.get(),
                        System.currentTimeMillis() - startMillis, !failed.get()));
    }

    /**
     * 会话列表：按 userId 列出其与指定智能体的全部会话（摘要 + 首次消息时间），按时间倒序
     */
    public List<AgentSession> listSessions(String apiKey, Long agentId, String userId) {
        authenticate(apiKey, agentId);
        if (StrUtil.isBlank(userId)) {
            throw new AgentProxyException("userId 不能为空");
        }
        AgentStateStore store = stateStore(agentId);
        List<AgentSession> sessions = new ArrayList<>();
        for (String sessionId : store.listSessionIds(userId)) {
            sessions.add(store.get(userId, sessionId, AGENT_STATE_KEY, AgentState.class)
                    .map(state -> AgentSession.builder()
                            .userId(userId)
                            .sessionId(sessionId)
                            .summary(summaryOf(state))
                            .timestamp(firstTimestampOf(state))
                            .build())
                    .orElse(AgentSession.builder().userId(userId).sessionId(sessionId).build()));
        }
        sessions.sort(Comparator.comparing(AgentSession::getTimestamp,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return sessions;
    }

    /**
     * 会话详情：一个会话的全部历史消息（AgentScope Msg 原样返回，不分页）
     */
    public List<Msg> listMessages(String apiKey, Long agentId, String userId, String sessionId) {
        authenticate(apiKey, agentId);
        checkSessionParams(userId, sessionId);
        return stateStore(agentId).get(userId, sessionId, AGENT_STATE_KEY, AgentState.class)
                .map(AgentState::getContext)
                .orElse(List.of());
    }

    /**
     * 删除会话：整个 (userId, sessionId) 的状态从存储中移除
     */
    public void deleteSession(String apiKey, Long agentId, String userId, String sessionId) {
        authenticate(apiKey, agentId);
        checkSessionParams(userId, sessionId);
        stateStore(agentId).delete(userId, sessionId);
    }

    /**
     * 中断会话：协作式中断正在进行的回复；会话未在运行时调用无实际效果
     */
    public void interruptSession(String apiKey, Long agentId, String userId, String sessionId) {
        authenticate(apiKey, agentId);
        checkSessionParams(userId, sessionId);
        HarnessAgent agent = agentRegistry.find(agentId);
        if (agent == null) {
            throw new AgentProxyException("智能体实例未注册（可能已删除或重建中）");
        }
        agent.getDelegate().interrupt(userId, sessionId);
    }

    /**
     * apiKey 鉴权 + 智能体访问权限校验。
     * 规则：key 存在且启用；智能体存在；key 归属用户是智能体创建人，或该用户是管理员。
     */
    private AgentInfo authenticate(String apiKey, Long agentId) {
        if (StrUtil.isBlank(apiKey)) {
            throw new AgentProxyException("缺少 ApiKey，请在请求头传入 X-Api-Key");
        }
        ApiKey key = apiKeyService.lambdaQuery().eq(ApiKey::getApiKey, apiKey).one();
        if (key == null) {
            throw new AgentProxyException("ApiKey 无效");
        }
        if (Integer.valueOf(0).equals(key.getStatus())) {
            throw new AgentProxyException("ApiKey 已禁用");
        }
        if (agentId == null) {
            throw new AgentProxyException("agentId 不能为空");
        }
        AgentInfo agent = agentInfoService.getById(agentId);
        if (agent == null) {
            throw new AgentProxyException("智能体不存在");
        }
        SysUser owner = key.getUserId() == null ? null : sysUserService.getById(key.getUserId());
        boolean admin = owner != null && Integer.valueOf(1).equals(owner.getIsAdmin());
        if (!admin && !Objects.equals(agent.getUserId(), key.getUserId())) {
            throw new AgentProxyException("无权访问该智能体");
        }
        return agent;
    }

    private AgentStateStore stateStore(Long agentId) {
        HarnessAgent agent = agentRegistry.find(agentId);
        if (agent == null) {
            throw new AgentProxyException("智能体实例未注册（可能已删除或重建中）");
        }
        return agent.getStateStore();
    }

    private void checkSessionParams(String userId, String sessionId) {
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(sessionId)) {
            throw new AgentProxyException("userId、sessionId 不能为空");
        }
    }

    /** 摘要：优先 AgentState 自带摘要，否则取首条用户消息截断 */
    private String summaryOf(AgentState state) {
        if (StrUtil.isNotBlank(state.getSummary())) {
            return state.getSummary();
        }
        List<Msg> context = state.getContext();
        if (context == null) {
            return null;
        }
        return context.stream()
                .filter(m -> MsgRole.USER == m.getRole())
                .map(Msg::getTextContent)
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .map(text -> StrUtil.maxLength(text, SUMMARY_MAX_LENGTH))
                .orElse(null);
    }

    /** 首次发送消息时间：首条用户消息时间戳，无用户消息时取首条消息 */
    private String firstTimestampOf(AgentState state) {
        List<Msg> context = state.getContext();
        if (context == null || context.isEmpty()) {
            return null;
        }
        return context.stream()
                .filter(m -> MsgRole.USER == m.getRole())
                .findFirst()
                .orElse(context.get(0))
                .getTimestamp();
    }

    /**
     * AgentScope 事件 -> SSE 事件转换器：每个对话流一个实例。
     * 工具入参增量按 toolCallId 累积（支持并行工具调用），ToolCallEndEvent 时输出完整参数；
     * 工具结果增量（ToolResultTextDeltaEvent）逐条输出。
     */
    static class SseConverter {

        static final String TYPE_AGENT_START = "agent_start";
        static final String TYPE_THINKING = "thinking";
        static final String TYPE_TEXT_BLOCK = "text_block";
        static final String TYPE_TOOL_CALL = "tool_call";
        static final String TYPE_TOOL_RESULT = "tool_result";
        static final String TYPE_AGENT_RESULT = "agent_result";
        static final String TYPE_AGENT_END = "agent_end";

        /** 工具参数累积器：toolCallId -> 参数 JSON 片段 */
        private final Map<String, StringBuilder> toolParamsAccumulator = new HashMap<>();

        /** 不关心的事件返回 null（被过滤掉） */
        AgentSseEvent convert(AgentEvent event) {
            if (event instanceof AgentStartEvent) {
                return AgentSseEvent.builder()
                        .type(TYPE_AGENT_START)
                        .content("")
                        .build();
            }
            if (event instanceof ThinkingBlockDeltaEvent e) {
                return AgentSseEvent.builder()
                        .type(TYPE_THINKING)
                        .content(e.getDelta())
                        .build();
            }
            if (event instanceof TextBlockDeltaEvent e) {
                return AgentSseEvent.builder()
                        .type(TYPE_TEXT_BLOCK)
                        .content(e.getDelta())
                        .build();
            }
            if (event instanceof ToolCallDeltaEvent e) {
                // 只累积参数，不输出
                toolParamsAccumulator.computeIfAbsent(e.getToolCallId(), k -> new StringBuilder())
                        .append(e.getDelta());
                return null;
            }
            if (event instanceof ToolCallEndEvent e) {
                String toolCallId = e.getToolCallId();
                StringBuilder accumulated = toolParamsAccumulator.remove(toolCallId);
                return AgentSseEvent.builder()
                        .type(TYPE_TOOL_CALL)
                        .toolCall(AgentSseEvent.ToolCallInfo.builder()
                                .toolCallId(toolCallId)
                                .toolName(e.getToolCallName())
                                .toolParams(accumulated == null ? "" : accumulated.toString())
                                .build())
                        .build();
            }
            if (event instanceof ToolResultTextDeltaEvent e) {
                return AgentSseEvent.builder()
                        .type(TYPE_TOOL_RESULT)
                        .toolCall(AgentSseEvent.ToolCallInfo.builder()
                                .toolCallId(e.getToolCallId())
                                .toolName(e.getToolCallName())
                                .toolResults(e.getDelta())
                                .build())
                        .build();
            }
            if (event instanceof AgentResultEvent e) {
                Msg result = e.getResult();
                return AgentSseEvent.builder()
                        .type(TYPE_AGENT_RESULT)
                        .content(result == null ? "" : StrUtil.nullToEmpty(result.getTextContent()))
                        .build();
            }
            if (event instanceof AgentEndEvent) {
                return AgentSseEvent.builder()
                        .type(TYPE_AGENT_END)
                        .content("")
                        .build();
            }
            return null;
        }
    }
}
