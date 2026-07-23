package com.example.agent.system.agent;

import cn.hutool.core.util.StrUtil;
import com.example.agent.system.entity.ModelConfig;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.anthropic.AnthropicChatModel;
import io.agentscope.extensions.model.anthropic.formatter.AnthropicChatFormatter;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 按模型配置（model_config 表）构建 AgentScope Model，构建失败回退全局默认模型 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelFactory {

    /** 全局默认模型（DashScope qwen-flash） */
    private final Model defaultModel;

    /**
     * 按配置构建模型；配置为空或供应商不支持时回退默认模型。
     * 每次对话实时构建，不做缓存，保证模型配置修改后立刻生效。
     */
    public Model fromConfig(ModelConfig config) {
        if (config == null || StrUtil.isBlank(config.getModel())) {
            return defaultModel;
        }
        try {
            return switch (StrUtil.nullToEmpty(config.getProvider())) {
                case "dashscope" -> buildDashScope(config);
                case "openai", "custom" -> buildOpenAi(config);
                case "anthropic" -> buildAnthropic(config);
                default -> {
                    log.warn("未知供应商 {}，回退默认模型", config.getProvider());
                    yield defaultModel;
                }
            };
        } catch (Exception e) {
            log.warn("构建模型 {} 失败，回退默认模型：{}", config.getModel(), e.getMessage());
            return defaultModel;
        }
    }

    private Model buildDashScope(ModelConfig config) {
        // apiKey 留空时回退平台默认的 YOKA_DASHSCOPE_API_KEY，让种子模型开箱即用
        String apiKey = StrUtil.blankToDefault(config.getApiKey(), System.getenv("YOKA_DASHSCOPE_API_KEY"));
        DashScopeChatModel.Builder builder = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(config.getModel())
                .stream(true)
                .formatter(new DashScopeChatFormatter());
        if (StrUtil.isNotBlank(config.getBaseUrl())) {
            builder.baseUrl(config.getBaseUrl());
        }
        return builder.build();
    }

    /** openai 与 custom 都走 OpenAI 兼容协议，custom 必须提供 baseUrl（保存时已校验） */
    private Model buildOpenAi(ModelConfig config) {
        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModel())
                .stream(true)
                .formatter(new OpenAIChatFormatter());
        if (StrUtil.isNotBlank(config.getBaseUrl())) {
            builder.baseUrl(config.getBaseUrl());
        }
        return builder.build();
    }

    private Model buildAnthropic(ModelConfig config) {
        AnthropicChatModel.Builder builder = AnthropicChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModel())
                .stream(true)
                .formatter(new AnthropicChatFormatter());
        if (StrUtil.isNotBlank(config.getBaseUrl())) {
            builder.baseUrl(config.getBaseUrl());
        }
        return builder.build();
    }
}
