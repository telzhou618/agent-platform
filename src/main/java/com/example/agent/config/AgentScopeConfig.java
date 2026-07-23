package com.example.agent.config;

import cn.hutool.core.util.StrUtil;
import com.example.agent.system.agent.DynamicAgentMiddleware;
import com.example.agent.system.service.ToolService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** AgentScope 全局配置：默认模型 + 全局工具箱 + 全局唯一智能体 */
@Slf4j
@Configuration
public class AgentScopeConfig {

    /** 全局默认模型：DashScope qwen-flash，apiKey 取环境变量 YOKA_DASHSCOPE_API_KEY */
    @Bean
    public Model defaultModel() {
        String apiKey = System.getenv("YOKA_DASHSCOPE_API_KEY");
        if (StrUtil.isBlank(apiKey)) {
            log.warn("环境变量 YOKA_DASHSCOPE_API_KEY 未设置，全局默认模型 qwen-flash 将无法调用");
        }
        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-flash")
                .stream(true)
                .formatter(new DashScopeChatFormatter())
                .build();
    }

    /** 全局工具箱：启动时即扫描注册全部系统工具（必须在 ReActAgent 创建前完成） */
    @Bean
    public Toolkit toolkit(ToolService toolService) {
        return toolService.buildToolkit();
    }

    /**
     * 全局唯一智能体实例。
     * 对话时的系统提示词、模型、工具由 {@link DynamicAgentMiddleware} 按智能体配置动态加载，
     * 因此所有会话共用这一个实例。
     */
    @Bean
    public ReActAgent reactAgent(Model defaultModel, Toolkit toolkit, DynamicAgentMiddleware dynamicAgentMiddleware) {
        return ReActAgent.builder()
                .name("agent-platform")
                .description("agent-platform 全局智能体")
                .sysPrompt("你是 agent-platform 的智能助手。")
                .model(defaultModel)
                .toolkit(toolkit)
                .middleware(dynamicAgentMiddleware)
                .build();
    }
}
