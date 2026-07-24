package com.example.agent.system.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.agent.system.agent.AgentRegistry;
import com.example.agent.system.entity.AgentInfo;
import com.example.agent.system.entity.McpServer;
import com.example.agent.system.mapper.AgentInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentInfoService extends ServiceImpl<AgentInfoMapper, AgentInfo> {

    private final AgentRegistry agentRegistry;
    private final ModelConfigService modelConfigService;
    private final McpServerService mcpServerService;

    /** 分页查询智能体，关键字匹配名称 / 描述 */
    public Page<AgentInfo> pageAgents(String keyword, int page, int size) {
        return lambdaQuery()
                .and(StrUtil.isNotBlank(keyword), q -> q
                        .like(AgentInfo::getName, keyword).or().like(AgentInfo::getDescription, keyword))
                .orderByDesc(AgentInfo::getCreateTime)
                .page(new Page<>(page, size));
    }

    /** 保存智能体（新增/编辑）：落库后同步注册/重建容器中的实例 */
    public void saveAgent(AgentInfo agent) {
        if (agent.getModelId() == null) {
            throw new IllegalArgumentException("请选择模型");
        }
        saveOrUpdate(agent);
        agentRegistry.register(agent, modelConfigService.getById(agent.getModelId()),
                mcpServersOf(agent));
    }

    /** 删除智能体：落库后同步销毁容器中的实例 */
    public void deleteAgent(Long id) {
        removeById(id);
        agentRegistry.unregister(id);
    }

    /** 解析 agent.mcpServers（JSON ID 数组）-> MCP 服务实体列表 */
    public List<McpServer> mcpServersOf(AgentInfo agent) {
        if (StrUtil.isBlank(agent.getMcpServers())) {
            return List.of();
        }
        List<Long> ids = JSONUtil.toList(agent.getMcpServers(), Long.class);
        return ids.isEmpty() ? List.of() : mcpServerService.listByIds(ids);
    }
}
