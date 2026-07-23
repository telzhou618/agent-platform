package com.example.agent.system.service;

import cn.hutool.json.JSONUtil;
import com.example.agent.system.dto.ToolInfo;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
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
public class ToolService implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;

    private volatile List<ToolInfo> tools = List.of();

    public ToolService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /** 所有单例 Bean 就绪后扫描并解析工具 */
    @Override
    public void afterSingletonsInstantiated() {
        Toolkit toolkit = new Toolkit();
        // 工具名 -> 来源类名
        List<ToolInfo> result = new ArrayList<>();
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Object bean = applicationContext.getBean(beanName);
            Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
            if (!hasToolMethod(targetClass)) {
                continue;
            }
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
                info.setSourceClass(targetClass.getSimpleName());
                result.add(info);
            }
            log.info("注册系统工具：{} -> {}", targetClass.getSimpleName(), added);
        }
        result.sort((a, b) -> a.getName().compareTo(b.getName()));
        this.tools = List.copyOf(result);
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
