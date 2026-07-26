package com.example.agent.system.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.example.agent.system.entity.AgentInfo;
import com.example.agent.system.entity.CustomTool;
import com.example.agent.system.entity.KnowledgeBase;
import com.example.agent.system.entity.McpServer;
import com.example.agent.system.entity.ModelConfig;
import com.example.agent.system.entity.SkillRepo;
import io.agentscope.core.model.Model;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.KnowledgeRetrievalTools;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能体实例注册中心：按 agent_info 配置把 HarnessAgent 动态注册为 Spring 单例 Bean，
 * 创建时即固化它的系统提示词、模型、系统工具、MCP 服务工具、知识库和技能仓库。
 * 管理端新增/编辑 -> register 重建；删除 -> unregister；启动 -> 全量 register；
 * 模型、MCP 服务、知识库或技能仓库变更/删除 -> onXxxChanged / onXxxDeleted 级联重建引用它的实例。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRegistry {

    private static final String BEAN_PREFIX = "harnessAgent#";
    /** 智能体未配置系统提示词时的兜底 */
    private static final String DEFAULT_SYS_PROMPT = "你是 agent-platform 的智能助手。";
    /** 追加在智能体系统提示词后的默认要求 */
    private static final String SYS_PROMPT_SUFFIX =
            "请始终以结构化的方式组织输出（分点、分段，必要时使用表格），回答问题简洁明了。";

    private final ConfigurableApplicationContext applicationContext;
    private final ModelFactory modelFactory;
    private final McpClientFactory mcpClientFactory;
    private final KnowledgeFactory knowledgeFactory;
    private final SkillRepoFactory skillRepoFactory;
    /** 全局工具箱：仅用于取系统 AgentTool 实例，组装各智能体的专属工具箱 */
    private final Toolkit toolkit;

    /** agentId -> 构建快照（智能体配置 + 模型 + MCP 服务 + 知识库 + 技能仓库），级联重建时使用 */
    private final Map<Long, BuildSnapshot> snapshots = new ConcurrentHashMap<>();
    /** agentId -> 该实例占用的 MCP 客户端，销毁实例时一并释放 */
    private final Map<Long, List<McpClientWrapper>> mcpClients = new ConcurrentHashMap<>();

    /** 注册智能体实例；已注册则先销毁重建（编辑场景） */
    public synchronized void register(AgentInfo agent, ModelConfig modelConfig, List<McpServer> mcpServers,
                                      List<KnowledgeBase> knowledgeBases, List<SkillRepo> skillRepos,
                                      List<CustomTool> customTools) {
        if (agent == null || agent.getId() == null) {
            return;
        }
        unregister(agent.getId());
        List<McpClientWrapper> clients = new ArrayList<>();
        List<KnowledgeBase> kbs = knowledgeBases == null ? List.of() : knowledgeBases;
        List<SkillRepo> repos = skillRepos == null ? List.of() : skillRepos;
        List<CustomTool> cts = customTools == null ? List.of() : customTools;
        beanFactory().registerSingleton(beanName(agent.getId()),
                build(agent, modelFactory.fromConfig(modelConfig),
                        mcpServers == null ? List.of() : mcpServers, kbs, repos, cts, clients));
        snapshots.put(agent.getId(),
                new BuildSnapshot(agent, modelConfig, List.copyOf(mcpServers == null ? List.of() : mcpServers),
                        List.copyOf(kbs), List.copyOf(repos), List.copyOf(cts)));
        mcpClients.put(agent.getId(), clients);
        log.info("注册智能体实例：{}（id={}）", agent.getName(), agent.getId());
    }

    /** 销毁智能体实例（管理端删除时调用），关闭 HarnessAgent 并释放其 MCP 客户端 */
    public synchronized void unregister(Long agentId) {
        String name = beanName(agentId);
        if (beanFactory().containsSingleton(name)) {
            Object instance = beanFactory().getSingleton(name);
            beanFactory().destroySingleton(name);
            if (instance instanceof HarnessAgent harnessAgent) {
                try {
                    harnessAgent.close();
                } catch (Exception e) {
                    log.warn("关闭智能体实例 id={} 失败：{}", agentId, e.getMessage());
                }
            }
            log.info("销毁智能体实例：id={}", agentId);
        }
        snapshots.remove(agentId);
        closeQuietly(mcpClients.remove(agentId));
    }

    /** 按 ID 查找智能体实例，未注册返回 null（调用方回退默认智能体） */
    public HarnessAgent find(Long agentId) {
        String name = beanName(agentId);
        return beanFactory().containsSingleton(name)
                ? (HarnessAgent) beanFactory().getSingleton(name) : null;
    }

    /** 模型配置变更：重建引用它的全部智能体实例 */
    public synchronized void onModelChanged(ModelConfig fresh) {
        snapshots.values().stream()
                .filter(s -> fresh.getId().equals(s.agent().getModelId()))
                .forEach(s -> register(s.agent(), fresh, s.mcpServers(), s.knowledgeBases(), s.skillRepos(),
                        s.customTools()));
    }

    /** 模型删除：引用它的智能体回退默认模型并重建 */
    public synchronized void onModelDeleted(Long modelId) {
        snapshots.values().stream()
                .filter(s -> modelId.equals(s.agent().getModelId()))
                .forEach(s -> register(s.agent(), null, s.mcpServers(), s.knowledgeBases(), s.skillRepos(),
                        s.customTools()));
    }

    /** MCP 服务变更：重建引用它的全部智能体实例 */
    public synchronized void onMcpChanged(McpServer fresh) {
        snapshots.values().stream()
                .filter(s -> references(s, fresh.getId()))
                .forEach(s -> register(s.agent(), s.model(), s.mcpServers().stream()
                        .map(m -> fresh.getId().equals(m.getId()) ? fresh : m)
                        .toList(), s.knowledgeBases(), s.skillRepos(), s.customTools()));
    }

    /** MCP 服务删除：重建引用它的全部智能体实例（移除该服务的工具） */
    public synchronized void onMcpDeleted(Long mcpServerId) {
        snapshots.values().stream()
                .filter(s -> references(s, mcpServerId))
                .forEach(s -> register(s.agent(), s.model(), s.mcpServers().stream()
                        .filter(m -> !mcpServerId.equals(m.getId()))
                        .toList(), s.knowledgeBases(), s.skillRepos(), s.customTools()));
    }

    /** 知识库变更：重建引用它的全部智能体实例 */
    public synchronized void onKnowledgeChanged(KnowledgeBase fresh) {
        snapshots.values().stream()
                .filter(s -> referencesKnowledge(s, fresh.getId()))
                .forEach(s -> register(s.agent(), s.model(), s.mcpServers(), s.knowledgeBases().stream()
                        .map(k -> fresh.getId().equals(k.getId()) ? fresh : k)
                        .toList(), s.skillRepos(), s.customTools()));
    }

    /** 知识库删除：重建引用它的全部智能体实例（移除该知识库） */
    public synchronized void onKnowledgeDeleted(Long knowledgeBaseId) {
        snapshots.values().stream()
                .filter(s -> referencesKnowledge(s, knowledgeBaseId))
                .forEach(s -> register(s.agent(), s.model(), s.mcpServers(), s.knowledgeBases().stream()
                        .filter(k -> !knowledgeBaseId.equals(k.getId()))
                        .toList(), s.skillRepos(), s.customTools()));
    }

    /** 技能仓库变更：重建引用它的全部智能体实例 */
    public synchronized void onSkillRepoChanged(SkillRepo fresh) {
        snapshots.values().stream()
                .filter(s -> referencesSkillRepo(s, fresh.getId()))
                .forEach(s -> register(s.agent(), s.model(), s.mcpServers(), s.knowledgeBases(),
                        s.skillRepos().stream()
                                .map(r -> fresh.getId().equals(r.getId()) ? fresh : r)
                                .toList(), s.customTools()));
    }

    /** 技能仓库删除：重建引用它的全部智能体实例（移除该来源） */
    public synchronized void onSkillRepoDeleted(Long skillRepoId) {
        snapshots.values().stream()
                .filter(s -> referencesSkillRepo(s, skillRepoId))
                .forEach(s -> register(s.agent(), s.model(), s.mcpServers(), s.knowledgeBases(),
                        s.skillRepos().stream()
                                .filter(r -> !skillRepoId.equals(r.getId()))
                                .toList(), s.customTools()));
    }

    /** 自定义工具变更：重建引用它的全部智能体实例 */
    public synchronized void onCustomToolChanged(CustomTool fresh) {
        snapshots.values().stream()
                .filter(s -> referencesCustomTool(s, fresh.getId()))
                .forEach(s -> register(s.agent(), s.model(), s.mcpServers(), s.knowledgeBases(), s.skillRepos(),
                        s.customTools().stream()
                                .map(t -> fresh.getId().equals(t.getId()) ? fresh : t)
                                .toList()));
    }

    /** 自定义工具删除：重建引用它的全部智能体实例（移除该工具） */
    public synchronized void onCustomToolDeleted(Long customToolId) {
        snapshots.values().stream()
                .filter(s -> referencesCustomTool(s, customToolId))
                .forEach(s -> register(s.agent(), s.model(), s.mcpServers(), s.knowledgeBases(), s.skillRepos(),
                        s.customTools().stream()
                                .filter(t -> !customToolId.equals(t.getId()))
                                .toList()));
    }

    private boolean references(BuildSnapshot snapshot, Long mcpServerId) {
        return snapshot.mcpServers().stream().anyMatch(m -> mcpServerId.equals(m.getId()));
    }

    private boolean referencesKnowledge(BuildSnapshot snapshot, Long knowledgeBaseId) {
        return snapshot.knowledgeBases().stream().anyMatch(k -> knowledgeBaseId.equals(k.getId()));
    }

    private boolean referencesSkillRepo(BuildSnapshot snapshot, Long skillRepoId) {
        return snapshot.skillRepos().stream().anyMatch(r -> skillRepoId.equals(r.getId()));
    }

    private boolean referencesCustomTool(BuildSnapshot snapshot, Long customToolId) {
        return snapshot.customTools().stream().anyMatch(t -> customToolId.equals(t.getId()));
    }

    /**
     * 组装实例：系统提示词 + 模型 + 专属工具箱（配置的系统工具 + 各 MCP 服务的工具 + 知识库检索工具）
     * + 技能仓库（HarnessAgent 官方技能体系）。
     * 知识库：HarnessAgent.Builder 无 knowledge()，与 ReActAgent AGENTIC 模式等价，
     * 把聚合后的 KnowledgeRetrievalTools 注册进工具箱，模型通过 retrieve_knowledge 工具自主检索。
     */
    // AgentScope 2.0.0 的 RAG API 标记为 deprecated-for-removal 但功能正常，按计划使用
    @SuppressWarnings("deprecation")
    private HarnessAgent build(AgentInfo agent, Model model, List<McpServer> mcpServers,
                               List<KnowledgeBase> knowledgeBases, List<SkillRepo> skillRepos,
                               List<CustomTool> customTools, List<McpClientWrapper> clientsOut) {
        Toolkit agentToolkit = new Toolkit();
        for (String toolName : parseToolNames(agent.getTools())) {
            AgentTool tool = toolkit.getTool(toolName);
            if (tool != null) {
                agentToolkit.registerAgentTool(tool);
            } else {
                log.warn("智能体「{}」配置的工具 {} 不存在，已跳过", agent.getName(), toolName);
            }
        }
        // 挂载自定义工具：HTTP 代理工具，单个失败只记日志跳过
        for (CustomTool customTool : customTools) {
            try {
                agentToolkit.registerAgentTool(new CustomHttpTool(customTool));
                log.info("智能体「{}」挂载自定义工具「{}」", agent.getName(), customTool.getToolKey());
            } catch (Exception e) {
                log.warn("智能体「{}」挂载自定义工具「{}」失败，已跳过：{}",
                        agent.getName(), customTool.getToolKey(), e.getMessage());
            }
        }
        for (McpServer server : mcpServers) {
            // 先做轻量探测：不可达直接跳过，不让 SDK 初始化失败刷错误堆栈
            if (!mcpClientFactory.reachable(server)) {
                log.warn("MCP 服务「{}」（{}）不可达，已跳过挂载", server.getName(), server.getUrl());
                continue;
            }
            try {
                McpClientWrapper client = mcpClientFactory.create(server);
                agentToolkit.registerMcpClient(client).block();
                clientsOut.add(client);
                log.info("智能体「{}」挂载 MCP 服务「{}」", agent.getName(), server.getName());
            } catch (Exception e) {
                log.warn("智能体「{}」挂载 MCP 服务「{}」失败，已跳过：{}",
                        agent.getName(), server.getName(), e.getMessage());
            }
        }
        // 挂载知识库：聚合成一个检索工具注册进工具箱；单个构建失败只记日志跳过
        List<Knowledge> knowledges = new ArrayList<>();
        for (KnowledgeBase kb : knowledgeBases) {
            try {
                knowledges.add(knowledgeFactory.fromConfig(kb));
                log.info("智能体「{}」挂载知识库「{}」", agent.getName(), kb.getName());
            } catch (Exception e) {
                log.warn("智能体「{}」挂载知识库「{}」失败，已跳过：{}",
                        agent.getName(), kb.getName(), e.getMessage());
            }
        }
        if (!knowledges.isEmpty()) {
            agentToolkit.registerTool(new KnowledgeRetrievalTools(new AggregatedKnowledge(knowledges)));
        }
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(agent.getName())
                .description(StrUtil.nullToEmpty(agent.getDescription()))
                .sysPrompt(buildSysPrompt(agent))
                .model(model)
                .toolkit(agentToolkit)
                .agentId("agent-" + agent.getId())
                .workspace("workspaces/agent-" + agent.getId())
                // 关闭 Harness 默认子系统：提示词/工具/技能由平台 DB 管理，
                // 不注入工作区文件、不开长期记忆、不开子 agent、不读 tools.json
                .disableWorkspaceContext()
                .disableMemoryHooks()
                .disableSubagents()
                .disableToolsConfig();
        // 挂载技能仓库：单个构建失败只记日志跳过，不影响智能体注册
        for (SkillRepo repo : skillRepos) {
            try {
                builder.skillRepository(skillRepoFactory.fromConfig(repo));
                log.info("智能体「{}」挂载技能仓库「{}」", agent.getName(), repo.getName());
            } catch (Exception e) {
                log.warn("智能体「{}」挂载技能仓库「{}」失败，已跳过：{}",
                        agent.getName(), repo.getName(), e.getMessage());
            }
        }
        return builder.build();
    }

    /** 系统提示词 = 用户配置（空则兜底）+ 默认输出要求 */
    private String buildSysPrompt(AgentInfo agent) {
        return StrUtil.blankToDefault(agent.getSysPrompt(), DEFAULT_SYS_PROMPT)
                + "\n" + SYS_PROMPT_SUFFIX;
    }

    private void closeQuietly(List<McpClientWrapper> clients) {
        if (clients == null) {
            return;
        }
        for (McpClientWrapper client : clients) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("关闭 MCP 客户端失败：{}", e.getMessage());
            }
        }
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

    /** 一次构建的全部输入，级联重建时回放 */
    private record BuildSnapshot(AgentInfo agent, ModelConfig model, List<McpServer> mcpServers,
                                 List<KnowledgeBase> knowledgeBases, List<SkillRepo> skillRepos,
                                 List<CustomTool> customTools) {
    }
}
