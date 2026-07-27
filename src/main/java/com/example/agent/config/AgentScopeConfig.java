package com.example.agent.config;

import cn.hutool.core.util.StrUtil;
import com.example.agent.system.service.ToolService;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope 全局配置：默认模型 + 全局工具箱 + 全局默认智能体（兜底）。
 * 各智能体的 HarnessAgent 实例由 AgentRegistry 按 agent_info 配置动态注册。
 */
@Slf4j
@Configuration
public class AgentScopeConfig {

    /**
     * 全局默认模型：DashScope qwen-flash，apiKey 取环境变量 YOKA_DASHSCOPE_API_KEY
     */
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
                .enableThinking(true)
                .defaultOptions(
                        GenerateOptions.builder()
                                .thinkingBudget(2048)
                                .build())
                .formatter(new DashScopeChatFormatter())
                .build();
    }

    /**
     * 全局工具箱：启动时即扫描注册全部系统工具（必须在 HarnessAgent 创建前完成）。
     * 各智能体的专属工具箱从这里取 AgentTool 实例组装。
     */
    @Bean
    public Toolkit toolkit(ToolService toolService) {
        return toolService.buildToolkit();
    }
}
