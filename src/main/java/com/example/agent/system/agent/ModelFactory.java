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

import java.util.Map;

/** 按模型配置（model_config 表）构建 AgentScope Model，构建失败回退全局默认模型 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelFactory {

    /** OpenAI 兼容供应商 -> 官方固定端点（仅 custom 自定义供应商需要手填 baseUrl） */
    private static final Map<String, String> OPENAI_COMPAT_ENDPOINTS = Map.of(
            "kimi", "https://api.moonshot.cn/v1",
            "deepseek", "https://api.deepseek.com/v1",
            "glm", "https://open.bigmodel.cn/api/paas/v4",
            "minimax", "https://api.minimaxi.com/v1");

    /** 全局默认模型（DashScope qwen-flash） */
    private final Model defaultModel;

    /**
     * 按配置构建模型；配置为空或构建失败时回退默认模型。
     * 每次对话实时构建，不做缓存，保证模型配置修改后立刻生效。
     */
    public Model fromConfig(ModelConfig config) {
        try {
            return buildStrict(config);
        } catch (Exception e) {
            log.warn("构建模型 {} 失败，回退默认模型：{}",
                    config == null ? null : config.getModel(), e.getMessage());
            return defaultModel;
        }
    }

    /**
     * 按配置严格构建模型：配置为空、供应商未知或构建失败时抛异常（不做兜底）。
     * 供可用性验证等需要真实结果的场景使用。
     */
    public Model buildStrict(ModelConfig config) {
        if (config == null || StrUtil.isBlank(config.getModel())) {
            throw new IllegalArgumentException("模型标识不能为空");
        }
        String provider = StrUtil.nullToEmpty(config.getProvider());
        if (OPENAI_COMPAT_ENDPOINTS.containsKey(provider)) {
            return buildOpenAi(config, OPENAI_COMPAT_ENDPOINTS.get(provider));
        }
        return switch (provider) {
            case "dashscope" -> buildDashScope(config);
            case "openai", "custom" -> buildOpenAi(config, null);
            case "anthropic" -> buildAnthropic(config);
            default -> throw new IllegalArgumentException("未知供应商 " + config.getProvider());
        };
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

    /**
     * openai / custom / kimi / deepseek / glm / minimax 都走 OpenAI 兼容协议。
     * defaultBaseUrl 为该供应商的官方固定端点（null 时用 SDK 默认，即 OpenAI 官方）；
     * config.baseUrl 非空时优先（历史数据或 custom 手填）。
     */
    private Model buildOpenAi(ModelConfig config, String defaultBaseUrl) {
        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModel())
                .stream(true)
                .formatter(new OpenAIChatFormatter());
        String baseUrl = StrUtil.blankToDefault(config.getBaseUrl(), defaultBaseUrl);
        if (StrUtil.isNotBlank(baseUrl)) {
            builder.baseUrl(baseUrl);
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
