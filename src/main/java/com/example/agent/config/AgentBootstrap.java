package com.example.agent.config;

import com.example.agent.system.agent.AgentRegistry;
import com.example.agent.system.entity.AgentInfo;
import com.example.agent.system.entity.McpServer;
import com.example.agent.system.entity.ModelConfig;
import com.example.agent.system.service.AgentInfoService;
import com.example.agent.system.service.McpServerService;
import com.example.agent.system.service.ModelConfigService;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 启动注册：项目启动时把 agent_info 表中的全部智能体注册为 Spring 容器中的
 * ReActAgent 实例（系统提示词、模型、工具在注册时固化）。
 * 单个注册失败不影响其它实例；数据库不可用时整体跳过，默认智能体仍可兜底对话。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentBootstrap implements ApplicationRunner {

    private final AgentInfoService agentInfoService;
    private final ModelConfigService modelConfigService;
    private final McpServerService mcpServerService;
    private final AgentRegistry agentRegistry;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Map<Long, ModelConfig> models = modelConfigService.list().stream()
                    .collect(Collectors.toMap(ModelConfig::getId, Function.identity()));
            Map<Long, McpServer> mcpServers = mcpServerService.list().stream()
                    .collect(Collectors.toMap(McpServer::getId, Function.identity()));
            List<AgentInfo> agents = agentInfoService.list();
            int ok = 0;
            for (AgentInfo agent : agents) {
                try {
                    agentRegistry.register(agent, models.get(agent.getModelId()),
                            mcpServersOf(agent, mcpServers));
                    ok++;
                } catch (Exception e) {
                    log.error("注册智能体「{}」失败：{}", agent.getName(), e.getMessage());
                }
            }
            log.info("启动注册智能体完成：{}/{} 个成功", ok, agents.size());
        } catch (Exception e) {
            log.error("启动注册智能体失败（数据库不可用？）：{}", e.getMessage());
        }
    }

    /** 按 agent.mcpServers（JSON ID 数组）从全量 Map 中解析 MCP 服务列表 */
    private List<McpServer> mcpServersOf(AgentInfo agent, Map<Long, McpServer> all) {
        if (StrUtil.isBlank(agent.getMcpServers())) {
            return List.of();
        }
        return JSONUtil.toList(agent.getMcpServers(), Long.class).stream()
                .map(all::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
