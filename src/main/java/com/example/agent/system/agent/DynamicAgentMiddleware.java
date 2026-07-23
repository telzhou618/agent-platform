package com.example.agent.system.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.model.ToolSchema;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

/**
 * 动态加载智能体配置的中间件：从 RuntimeContext 读取 {@link AgentRuntimeConfig}，
 * 分别通过三个钩子实现——系统提示词用 onSystemPrompt 替换，
 * 模型用 onModelCall 切换，工具用 onReasoning 过滤（只暴露配置的工具，全局工具箱保持不变）。
 * 上下文里没有配置时全部透传，等价于全局默认行为。
 */
@Component
public class DynamicAgentMiddleware implements MiddlewareBase {

    /** 动态系统提示词：替换为智能体配置的提示词 */
    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext ctx, String currentPrompt) {
        AgentRuntimeConfig config = ctx.get(AgentRuntimeConfig.class);
        if (config != null && StrUtil.isNotBlank(config.getSysPrompt())) {
            return Mono.just(config.getSysPrompt());
        }
        return Mono.just(currentPrompt);
    }

    /** 动态工具：只保留智能体配置的工具；配置为空列表时不暴露任何工具 */
    @Override
    public Flux<AgentEvent> onReasoning(Agent agent, RuntimeContext ctx, ReasoningInput input,
                                        Function<ReasoningInput, Flux<AgentEvent>> next) {
        AgentRuntimeConfig config = ctx.get(AgentRuntimeConfig.class);
        if (config == null || config.getToolNames() == null) {
            return next.apply(input);
        }
        List<ToolSchema> tools = CollUtil.isEmpty(config.getToolNames())
                ? List.of()
                : input.tools().stream()
                        .filter(t -> config.getToolNames().contains(t.getName()))
                        .toList();
        return next.apply(new ReasoningInput(input.messages(), tools, input.options()));
    }

    /** 动态模型：切换为智能体配置的模型 */
    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext ctx, ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        AgentRuntimeConfig config = ctx.get(AgentRuntimeConfig.class);
        if (config == null || config.getModel() == null) {
            return next.apply(input);
        }
        return next.apply(new ModelCallInput(input.messages(), input.tools(), input.options(), config.getModel()));
    }
}
