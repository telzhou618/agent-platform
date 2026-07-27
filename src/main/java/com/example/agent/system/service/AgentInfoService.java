package com.example.agent.system.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.agent.system.agent.AgentRegistry;
import com.example.agent.system.entity.AgentInfo;
import com.example.agent.system.entity.CustomTool;
import com.example.agent.system.entity.KnowledgeBase;
import com.example.agent.system.entity.McpServer;
import com.example.agent.system.entity.SkillRepo;
import com.example.agent.system.log.OperationLog;
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
    private final KnowledgeBaseService knowledgeBaseService;
    private final SkillRepoService skillRepoService;
    private final CustomToolService customToolService;

    /**
     * 分页查询智能体，关键字匹配名称 / 描述
     */
    public Page<AgentInfo> pageAgents(String keyword, int page, int size) {
        return lambdaQuery()
                .and(StrUtil.isNotBlank(keyword), q -> q
                        .like(AgentInfo::getName, keyword).or().like(AgentInfo::getDescription, keyword))
                .orderByDesc(AgentInfo::getCreateTime)
                .page(new Page<>(page, size));
    }

    /**
     * 保存智能体（新增/编辑）：落库后同步注册/重建容器中的实例
     */
    @OperationLog(module = "智能体管理", action = "保存", summary = "#agent.name")
    public void saveAgent(AgentInfo agent) {
        if (agent.getModelId() == null) {
            throw new IllegalArgumentException("请选择模型");
        }

        // 可清除置信息
        agent.setTools(agent.getTools() == null ? "[]" : agent.getTools());
        agent.setCustomTools(agent.getCustomTools() == null ? "[]" : agent.getCustomTools());
        agent.setMcpServers(agent.getMcpServers() == null ? "[]" : agent.getMcpServers());
        agent.setKnowledgeBases(agent.getKnowledgeBases() == null ? "[]" : agent.getKnowledgeBases());
        agent.setSkillRepos(agent.getSkillRepos() == null ? "[]" : agent.getSkillRepos());

        saveOrUpdate(agent);
        agentRegistry.register(agent, modelConfigService.getById(agent.getModelId()),
                mcpServersOf(agent), knowledgeBasesOf(agent), skillReposOf(agent), customToolsOf(agent));
    }

    /**
     * 删除智能体：落库后同步销毁容器中的实例
     */
    @OperationLog(module = "智能体管理", action = "删除", summary = "#id")
    public void deleteAgent(Long id) {
        removeById(id);
        agentRegistry.unregister(id);
    }

    /**
     * 解析 agent.mcpServers（JSON ID 数组）-> MCP 服务实体列表
     */
    public List<McpServer> mcpServersOf(AgentInfo agent) {
        if (StrUtil.isBlank(agent.getMcpServers())) {
            return List.of();
        }
        List<Long> ids = JSONUtil.toList(agent.getMcpServers(), Long.class);
        return ids.isEmpty() ? List.of() : mcpServerService.listByIds(ids);
    }

    /**
     * 解析 agent.knowledgeBases（JSON ID 数组）-> 知识库实体列表
     */
    public List<KnowledgeBase> knowledgeBasesOf(AgentInfo agent) {
        if (StrUtil.isBlank(agent.getKnowledgeBases())) {
            return List.of();
        }
        List<Long> ids = JSONUtil.toList(agent.getKnowledgeBases(), Long.class);
        return ids.isEmpty() ? List.of() : knowledgeBaseService.listByIds(ids);
    }

    /**
     * 解析 agent.skillRepos（JSON ID 数组）-> 技能仓库实体列表
     */
    public List<SkillRepo> skillReposOf(AgentInfo agent) {
        if (StrUtil.isBlank(agent.getSkillRepos())) {
            return List.of();
        }
        List<Long> ids = JSONUtil.toList(agent.getSkillRepos(), Long.class);
        return ids.isEmpty() ? List.of() : skillRepoService.listByIds(ids);
    }

    /**
     * 解析 agent.customTools（JSON ID 数组）-> 自定义工具实体列表
     */
    public List<CustomTool> customToolsOf(AgentInfo agent) {
        if (StrUtil.isBlank(agent.getCustomTools())) {
            return List.of();
        }
        List<Long> ids = JSONUtil.toList(agent.getCustomTools(), Long.class);
        return ids.isEmpty() ? List.of() : customToolService.listByIds(ids);
    }
}
