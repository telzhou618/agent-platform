package com.example.agent;

import com.example.agent.system.service.AgentInfoService;
import com.example.agent.system.service.ChatRecordService;
import com.example.agent.system.service.McpServerService;
import com.example.agent.system.service.ModelConfigService;
import com.example.agent.system.service.ToolService;
import com.example.agent.ui.HomeView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dashboard：验证统计查询可执行、首页能正常构建（需要本地 MySQL） */
@SpringBootTest
class HomeViewTest {

    @Autowired
    private ModelConfigService modelConfigService;
    @Autowired
    private AgentInfoService agentInfoService;
    @Autowired
    private ToolService toolService;
    @Autowired
    private McpServerService mcpServerService;
    @Autowired
    private ChatRecordService chatRecordService;

    @Test
    void 首页正常构建() {
        HomeView view = new HomeView(modelConfigService, agentInfoService, toolService,
                mcpServerService, chatRecordService);
        // 标题 + 简介 + 卡片区 + 面板区
        assertEquals(4, view.getChildren().count());
    }

    @Test
    void 趋势固定返回7天() {
        assertEquals(7, chatRecordService.weeklyTrend().size());
    }

    @Test
    void 活跃榜不超过5条() {
        assertTrue(chatRecordService.topActiveAgents().size() <= 5);
    }
}
