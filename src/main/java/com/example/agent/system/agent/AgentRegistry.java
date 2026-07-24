package com.example.agent.system.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.example.agent.system.entity.AgentInfo;
import com.example.agent.system.entity.ModelConfig;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能体实例注册中心：按 agent_info 配置把 ReActAgent 动态注册为 Spring 单例 Bean，
 * 创建时即固化它的系统提示词、模型和专属工具箱（不再依赖运行时中间件）。
 * 管理端新增/编辑 -> register 重建；删除 -> unregister；启动 -> 全量 register；
 * 模型配置变更/删除 -> onModelChanged / onModelDeleted 级联重建引用它的实例。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRegistry {

    private static final String BEAN_PREFIX = "reactAgent#";
    /** 智能体未配置系统提示词时的兜底 */
    private static final String DEFAULT_SYS_PROMPT = "你是 agent-platform 的智能助手。";

    private final ConfigurableApplicationContext applicationContext;
    private final ModelFactory modelFactory;
    /** 全局工具箱：仅用于取 AgentTool 实例，组装各智能体的专属工具箱 */
    private final Toolkit toolkit;

    /** agentId -> 注册时的配置快照，模型变更级联重建时使用 */
    private final Map<Long, AgentInfo> agents = new ConcurrentHashMap<>();

    /** 注册智能体实例；已注册则先销毁重建（编辑场景） */
    public synchronized void register(AgentInfo agent, ModelConfig modelConfig) {
        if (agent == null || agent.getId() == null) {
            return;
        }
        unregister(agent.getId());
        beanFactory().registerSingleton(beanName(agent.getId()),
                build(agent, modelFactory.fromConfig(modelConfig)));
        agents.put(agent.getId(), agent);
        log.info("注册智能体实例：{}（id={}）", agent.getName(), agent.getId());
    }

    /** 销毁智能体实例（管理端删除时调用） */
    public synchronized void unregister(Long agentId) {
        String name = beanName(agentId);
        if (beanFactory().containsSingleton(name)) {
            beanFactory().destroySingleton(name);
            agents.remove(agentId);
            log.info("销毁智能体实例：id={}", agentId);
        }
    }

    /** 按 ID 查找智能体实例，未注册返回 null（调用方回退默认智能体） */
    public ReActAgent find(Long agentId) {
        String name = beanName(agentId);
        return beanFactory().containsSingleton(name)
                ? (ReActAgent) beanFactory().getSingleton(name) : null;
    }

    /** 模型配置变更：重建引用它的全部智能体实例 */
    public synchronized void onModelChanged(ModelConfig fresh) {
        rebuild(fresh.getId(), fresh);
    }

    /** 模型删除：引用它的智能体回退默认模型并重建 */
    public synchronized void onModelDeleted(Long modelId) {
        rebuild(modelId, null);
    }

    private void rebuild(Long modelId, ModelConfig fresh) {
        agents.values().stream()
                .filter(a -> modelId.equals(a.getModelId()))
                .forEach(a -> register(a, fresh));
    }

    /** 组装实例：系统提示词 + 模型 + 专属工具箱（只含配置的工具） */
    private ReActAgent build(AgentInfo agent, Model model) {
        Toolkit agentToolkit = new Toolkit();
        for (String toolName : parseToolNames(agent.getTools())) {
            AgentTool tool = toolkit.getTool(toolName);
            if (tool != null) {
                agentToolkit.registerAgentTool(tool);
            } else {
                log.warn("智能体「{}」配置的工具 {} 不存在，已跳过", agent.getName(), toolName);
            }
        }
        return ReActAgent.builder()
                .name(agent.getName())
                .description(StrUtil.nullToEmpty(agent.getDescription()))
                .sysPrompt(StrUtil.blankToDefault(agent.getSysPrompt(), DEFAULT_SYS_PROMPT))
                .model(model)
                .toolkit(agentToolkit)
                .build();
    }

    /** JSON 数组字符串 -> 工具名列表 */
    private List<String> parseToolNames(String toolsJson) {
        return StrUtil.isBlank(toolsJson) ? List.of() : JSONUtil.toList(toolsJson, String.class);
    }

    private String beanName(Long agentId) {
        return BEAN_PREFIX + agentId;
    }

    private DefaultListableBeanFactory beanFactory() {
        return (DefaultListableBeanFactory) applicationContext.getBeanFactory();
    }
}
