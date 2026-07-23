package com.example.agent.system.agent;

import io.agentscope.core.model.Model;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 单次对话的动态运行配置：随 RuntimeContext 传入，由 DynamicAgentMiddleware 读取生效 */
@Data
@AllArgsConstructor
public class AgentRuntimeConfig {

    /** 系统提示词，null 或空白则用全局默认提示词 */
    private String sysPrompt;

    /** 聊天模型，null 用全局默认模型 */
    private Model model;

    /** 允许使用的工具名列表，空列表表示不使用工具 */
    private List<String> toolNames;
}
