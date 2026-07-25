package com.example.agent.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.agent.system.agent.AgentRegistry;
import com.example.agent.system.agent.McpClientFactory;
import com.example.agent.system.entity.McpServer;
import com.example.agent.system.log.OperationLog;
import com.example.agent.system.mapper.McpServerMapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class McpServerService extends ServiceImpl<McpServerMapper, McpServer> {

    private final AgentRegistry agentRegistry;
    private final McpClientFactory mcpClientFactory;

    /** 分页查询 MCP 服务，关键字匹配名称 / 描述 */
    public Page<McpServer> pageMcpServers(String keyword, int page, int size) {
        return lambdaQuery()
                .and(StrUtil.isNotBlank(keyword), q -> q
                        .like(McpServer::getName, keyword).or().like(McpServer::getDescription, keyword))
                .orderByDesc(McpServer::getCreateTime)
                .page(new Page<>(page, size));
    }

    /**
     * 保存 MCP 服务（新增/编辑）：先验证可连接，不可连接则报错放弃入库；
     * 落库后级联重建引用它的智能体实例。
     */
    @OperationLog(module = "MCP服务管理", action = "保存", summary = "#server.name")
    public void saveMcpServer(McpServer server) {
        if (StrUtil.isBlank(server.getName())) {
            throw new IllegalArgumentException("名称不能为空");
        }
        if (StrUtil.isBlank(server.getUrl())) {
            throw new IllegalArgumentException("URL 不能为空");
        }
        String error = mcpClientFactory.testConnection(server);
        if (error != null) {
            throw new IllegalStateException("MCP 服务连接失败，已放弃保存：" + error);
        }
        saveOrUpdate(server);
        agentRegistry.onMcpChanged(server);
    }

    /** 删除 MCP 服务：落库后级联重建引用它的智能体实例（移除该服务的工具） */
    @OperationLog(module = "MCP服务管理", action = "删除", summary = "#id")
    public void deleteMcpServer(Long id) {
        removeById(id);
        agentRegistry.onMcpDeleted(id);
    }

    /** 检测服务可用状态：返回 null 表示可用，否则返回错误信息 */
    public String testConnection(McpServer server) {
        return mcpClientFactory.testConnection(server);
    }

    /** 从 MCP 服务实时拉取工具列表 */
    public List<McpSchema.Tool> listTools(McpServer server) {
        return mcpClientFactory.listTools(server);
    }
}
