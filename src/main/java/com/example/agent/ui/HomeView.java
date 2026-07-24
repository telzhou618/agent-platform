package com.example.agent.ui;

import com.example.agent.system.dto.AgentActivityStat;
import com.example.agent.system.dto.DailyCount;
import com.example.agent.system.service.AgentInfoService;
import com.example.agent.system.service.ChatRecordService;
import com.example.agent.system.service.McpServerService;
import com.example.agent.system.service.ModelConfigService;
import com.example.agent.system.service.ToolService;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 首页 dashboard：平台资源概览 + 对话活跃统计（数据来自 chat_record 埋点）。样式见 styles/home.css。
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("首页 - agent-platform")
@StyleSheet("context://styles/home.css")
public class HomeView extends VerticalLayout {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public HomeView(ModelConfigService modelConfigService, AgentInfoService agentInfoService,
                    ToolService toolService, McpServerService mcpServerService,
                    ChatRecordService chatRecordService) {

        H2 title = new H2("Agent 管理平台");
        title.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-xl)");
        title.addClassName("home-title");
        Paragraph intro = new Paragraph("基于 AgentScope + Spring Boot + Vaadin 的综合性 Agent 管理平台。");
        intro.addClassName("home-intro");

        add(title, intro, statCards(modelConfigService, agentInfoService, toolService, mcpServerService));

        Div panels = new Div(trendPanel(chatRecordService.weeklyTrend()),
                activePanel(chatRecordService.topActiveAgents()));
        panels.addClassNames("home-flex-row", "home-panels");
        add(panels);
    }

    // ---------- 顶部统计卡片 ----------

    private Div statCards(ModelConfigService modelConfigService, AgentInfoService agentInfoService,
                          ToolService toolService, McpServerService mcpServerService) {
        Div cards = new Div(
                statCard("模型数量", modelConfigService.count(), VaadinIcon.DATABASE, "blue", "models"),
                statCard("智能体数量", agentInfoService.count(), VaadinIcon.CLUSTER, "green", "agents"),
                statCard("工具数量", toolService.listTools().size(), VaadinIcon.TOOLS, "orange", "tools"),
                statCard("MCP服务数量", mcpServerService.count(), VaadinIcon.PLUG, "purple", "mcp"));
        cards.addClassName("home-flex-row");
        return cards;
    }

    /**
     * 单个统计卡片：图标 + 数值 + 名称，点击跳转对应管理页；variant 对应 stat-card-* 主题色变体
     */
    private Div statCard(String label, long value, VaadinIcon vaadinIcon, String variant, String route) {
        Icon icon = vaadinIcon.create();
        icon.addClassName("stat-card-icon");

        Div number = new Div();
        number.setText(String.valueOf(value));
        number.addClassName("stat-card-number");
        Div text = new Div();
        text.setText(label);
        text.addClassName("stat-card-label");

        Div card = new Div(icon, new Div(number, text));
        card.addClassNames("stat-card", "stat-card-" + variant);
        card.addClickListener(e -> getUI().ifPresent(ui -> ui.navigate(route)));
        return card;
    }

    // ---------- 近 7 日对话趋势 ----------

    private Div trendPanel(List<DailyCount> trend) {
        Div panel = panel("近 7 日对话趋势");
        long max = trend.stream().mapToLong(DailyCount::getCount).max().orElse(0);
        LocalDate today = LocalDate.now();

        Div chart = new Div();
        chart.addClassName("trend-chart");
        for (DailyCount day : trend) {
            Span count = span(String.valueOf(day.getCount()), "trend-count");

            Div bar = new Div();
            // 高度按比例，有数据时至少露出一小节，今日高亮
            int percent = max == 0 ? 0 : (int) Math.round(day.getCount() * 100.0 / max);
            bar.addClassName("trend-bar");
            if (day.getDate().equals(today)) {
                bar.addClassName("trend-bar-today");
            }
            bar.getStyle().set("height", Math.max(percent, day.getCount() > 0 ? 4 : 0) + "%");
            Div barWrap = new Div(bar);
            barWrap.addClassName("trend-bar-wrap");

            Span label = span(day.getDate().format(DAY_FORMAT), "trend-label");

            Div column = new Div(count, barWrap, label);
            column.addClassName("trend-column");
            chart.add(column);
        }
        panel.add(chart);
        return panel;
    }

    // ---------- 最近活跃智能体 ----------

    private Div activePanel(List<AgentActivityStat> stats) {
        Div panel = panel("最近活跃智能体");
        if (stats.isEmpty()) {
            Div empty = new Div();
            empty.setText("暂无对话记录，去「流式对话」聊聊吧");
            empty.addClassName("active-empty");
            panel.add(empty);
            return panel;
        }
        for (AgentActivityStat stat : stats) {
            panel.add(activeRow(stat));
        }
        return panel;
    }

    /**
     * 活跃智能体行：头像 + 名称与指标 + 成功率与最近活跃时间
     */
    private Div activeRow(AgentActivityStat stat) {
        Avatar avatar = new Avatar(stat.getAgentName());
        avatar.setColorIndex((int) (stat.getAgentId() % 7));

        Span name = span(stat.getAgentName(), "active-row-name");
        Span metrics = span("近7天 " + stat.getWeekCount() + " 轮 · 共 " + stat.getTotalCount() + " 轮 · "
                        + stat.getSessionCount() + " 个会话 · 工具 " + stat.getToolCallCount() + " 次",
                "active-row-metrics");
        Div middle = new Div(name, metrics);
        middle.addClassName("active-row-middle");

        Span rate = span("成功率 " + stat.getSuccessRate() + "%", "active-row-rate");
        Span time = span(relativeTime(stat.getLastActiveTime()), "active-row-time");
        Div right = new Div(rate, time);
        right.addClassName("active-row-right");

        Div row = new Div(avatar, middle, right);
        row.addClassName("active-row");
        return row;
    }

    private static String relativeTime(LocalDateTime time) {
        Duration duration = Duration.between(time, LocalDateTime.now());
        if (duration.toMinutes() < 1) {
            return "刚刚";
        }
        if (duration.toHours() < 1) {
            return duration.toMinutes() + " 分钟前";
        }
        if (duration.toDays() < 1) {
            return duration.toHours() + " 小时前";
        }
        if (duration.toDays() < 30) {
            return duration.toDays() + " 天前";
        }
        return time.format(TIME_FORMAT);
    }

    // ---------- 组件小工具 ----------

    /**
     * 面板容器：标题 + 白底圆角边框
     */
    private Div panel(String title) {
        H3 heading = new H3(title);
        heading.addClassName("home-panel-heading");
        Div panel = new Div(heading);
        panel.addClassName("home-panel");
        return panel;
    }

    private static Span span(String content, String className) {
        Span span = new Span(content);
        span.addClassName(className);
        return span;
    }
}
