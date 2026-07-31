package com.example.agent;

import com.example.agent.system.service.AgentInfoService;
import com.example.agent.system.service.ApiKeyService;
import com.example.agent.system.service.ChatRecordService;
import com.example.agent.system.service.CustomToolService;
import com.example.agent.system.service.KnowledgeBaseService;
import com.example.agent.system.service.McpServerService;
import com.example.agent.system.service.ModelConfigService;
import com.example.agent.system.service.SkillRepoService;
import com.example.agent.system.service.TokenUsageService;
import com.example.agent.system.service.ToolService;
import com.example.agent.ui.view.DashboardView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dashboard：验证统计查询可执行、首页数据看板能正常构建（需要本地 MySQL） */
@SpringBootTest
class DashboardViewTest {

    @Autowired
    private ModelConfigService modelConfigService;
    @Autowired
    private AgentInfoService agentInfoService;
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    @Autowired
    private SkillRepoService skillRepoService;
    @Autowired
    private ToolService toolService;
    @Autowired
    private CustomToolService customToolService;
    @Autowired
    private McpServerService mcpServerService;
    @Autowired
    private ApiKeyService apiKeyService;
    @Autowired
    private ChatRecordService chatRecordService;
    @Autowired
    private TokenUsageService tokenUsageService;

    @Test
    void dashboardBuildsSuccessfully() {
        DashboardView view = new DashboardView(modelConfigService, agentInfoService, knowledgeBaseService,
                skillRepoService, toolService, customToolService, mcpServerService, apiKeyService,
                chatRecordService, tokenUsageService);
        // Hero + 统计卡 + 趋势/概览行 + 排行/模型行 + 快捷入口 + 脚注
        assertEquals(6, view.getChildren().count());
    }

    @Test
    void weeklyTrendReturns7Days() {
        assertEquals(7, chatRecordService.weeklyTrend().size());
    }

    @Test
    void topActiveAgentsAtMost5() {
        assertTrue(chatRecordService.topActiveAgents().size() <= 5);
    }
}
