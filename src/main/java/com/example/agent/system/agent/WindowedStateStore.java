package com.example.agent.system.agent;

import io.agentscope.core.message.Msg;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 上下文窗口装饰器：包装任意 {@link AgentStateStore}，在加载会话状态（{@link AgentState}）时
 * 把上下文消息裁剪为最近 N 条，实现智能体的「上下文数」配置。
 * 裁剪后的状态在下一轮对话结束保存时会写回存储，即超出窗口的旧消息随窗口自然淘汰。
 * 其余方法（保存、删除、列举会话等）全部透传给底层存储，不改变其语义。
 */
@Slf4j
public class WindowedStateStore implements AgentStateStore {

    private final AgentStateStore delegate;
    private final int maxMessages;

    private WindowedStateStore(AgentStateStore delegate, int maxMessages) {
        this.delegate = delegate;
        this.maxMessages = maxMessages;
    }

    /**
     * 包装底层存储；窗口大小非法（null/&lt;=0）时不做包装，直接返回原存储
     */
    public static AgentStateStore wrap(AgentStateStore delegate, Integer maxMessages) {
        if (delegate == null || maxMessages == null || maxMessages <= 0) {
            return delegate;
        }
        return new WindowedStateStore(delegate, maxMessages);
    }

    @Override
    public void save(String userId, String sessionId, String key, State state) {
        delegate.save(userId, sessionId, key, state);
    }

    @Override
    public void save(String userId, String sessionId, String key, List<? extends State> states) {
        delegate.save(userId, sessionId, key, states);
    }

    @Override
    public <T extends State> Optional<T> get(String userId, String sessionId, String key, Class<T> type) {
        Optional<T> result = delegate.get(userId, sessionId, key, type);
        result.ifPresent(this::trimToWindow);
        return result;
    }

    @Override
    public <T extends State> List<T> getList(String userId, String sessionId, String key, Class<T> type) {
        List<T> result = delegate.getList(userId, sessionId, key, type);
        result.forEach(this::trimToWindow);
        return result;
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        return delegate.exists(userId, sessionId);
    }

    @Override
    public void delete(String userId, String sessionId) {
        delegate.delete(userId, sessionId);
    }

    @Override
    public void delete(String userId, String sessionId, String key) {
        delegate.delete(userId, sessionId, key);
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        return delegate.listSessionIds(userId);
    }

    @Override
    public void close() {
        delegate.close();
    }

    /** 上下文超过窗口时裁剪为最近 maxMessages 条；裁剪失败只记日志，返回未裁剪状态 */
    private void trimToWindow(State state) {
        if (!(state instanceof AgentState agentState)) {
            return;
        }
        try {
            List<Msg> context = agentState.getContext();
            if (context == null || context.size() <= maxMessages) {
                return;
            }
            List<Msg> windowed = new ArrayList<>(context.subList(context.size() - maxMessages, context.size()));
            List<Msg> mutable = agentState.contextMutable();
            mutable.clear();
            mutable.addAll(windowed);
        } catch (Exception e) {
            log.warn("裁剪会话上下文到最近 {} 条失败：{}", maxMessages, e.getMessage());
        }
    }
}
