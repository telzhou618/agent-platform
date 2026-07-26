package com.example.agent.system.service;

import cn.hutool.json.JSONUtil;
import com.example.agent.system.dto.ToolInfo;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 工具管理：扫描 Spring 容器中所有带 {@link Tool @Tool} 注解方法的 Bean，
 * 借助 AgentScope 的 {@link Toolkit} 反射注册并解析出工具名称、描述、参数 JSON Schema。
 * 系统工具不落库，新增工具只需再写一个带 @Tool 注解的 @Component。
 */
@Slf4j
@Service
public class ToolService {

    private final ApplicationContext applicationContext;

    private volatile List<ToolInfo> tools = List.of();

    public ToolService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 构建全局工具箱：扫描并注册全部系统工具。
     * 由 AgentScopeConfig#toolkit 调用，必须在 HarnessAgent 创建前完成——
     * HarnessAgent 构建时即固化工具箱快照，之后注册的工具不会生效。
     * 工具一律不分组（ungrouped）：AgentScope 每次对话会用会话状态覆盖工具箱的激活组，
     * 分组工具对新会话不可见，而不分组工具始终对模型可见（再由 DynamicAgentMiddleware 按智能体配置过滤）。
     */
    public Toolkit buildToolkit() {
        Toolkit toolkit = new Toolkit();
        // 工具名 -> 来源类名
        List<ToolInfo> result = new ArrayList<>();
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> type = applicationContext.getType(beanName);
            if (type == null || !hasToolMethod(type)) {
                continue;
            }
            Object bean = applicationContext.getBean(beanName);
            Set<String> before = new HashSet<>(toolkit.getToolNames());
            toolkit.registerTool(bean);
            Set<String> added = new HashSet<>(toolkit.getToolNames());
            added.removeAll(before);
            for (String name : added) {
                AgentTool agentTool = toolkit.getTool(name);
                ToolInfo info = new ToolInfo();
                info.setName(agentTool.getName());
                info.setDescription(agentTool.getDescription());
                info.setParamsJson(JSONUtil.toJsonPrettyStr(agentTool.getParameters()));
                info.setType("系统工具");
                info.setSourceClass(AopProxyUtils.ultimateTargetClass(bean).getSimpleName());
                result.add(info);
            }
            log.info("注册系统工具：{} -> {}", type.getSimpleName(), added);
        }
        result.sort((a, b) -> a.getName().compareTo(b.getName()));
        this.tools = List.copyOf(result);
        return toolkit;
    }

    /** 全部系统工具 */
    public List<ToolInfo> listTools() {
        return tools;
    }

    /** 全部系统工具名称（智能体配置工具列表时使用） */
    public List<String> listToolNames() {
        return tools.stream().map(ToolInfo::getName).toList();
    }

    private boolean hasToolMethod(Class<?> clazz) {
        for (var method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                return true;
            }
        }
        return false;
    }
}
