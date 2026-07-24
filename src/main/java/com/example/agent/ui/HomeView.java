package com.example.agent.ui;

import com.example.agent.system.dto.AgentActivityStat;
import com.example.agent.system.dto.DailyCount;
import com.example.agent.system.service.AgentInfoService;
import com.example.agent.system.service.ChatRecordService;
import com.example.agent.system.service.McpServerService;
import com.example.agent.system.service.ModelConfigService;
import com.example.agent.system.service.ToolService;
import com.vaadin.flow.component.avatar.Avatar;
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

/** 首页 dashboard：平台资源概览 + 对话活跃统计（数据来自 chat_record 埋点）。样式全部内联，不依赖 app.css。 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("首页 - agent-platform")
public class HomeView extends VerticalLayout {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 卡片主题色：[主色, 图标底色] */
    private static final String[] BLUE = {"var(--lumo-primary-color)", "var(--lumo-primary-color-10pct)"};
    private static final String[] GREEN = {"var(--lumo-success-color)", "var(--lumo-success-color-10pct)"};
    private static final String[] ORANGE = {"#ed7c31", "rgba(237, 124, 49, 0.12)"};
    private static final String[] PURPLE = {"#7b5ea7", "rgba(123, 94, 167, 0.12)"};

    public HomeView(ModelConfigService modelConfigService, AgentInfoService agentInfoService,
                    ToolService toolService, McpServerService mcpServerService,
                    ChatRecordService chatRecordService) {
        setMaxWidth("1200px");

        H2 title = new H2("Agent 管理平台");
        title.getStyle().set("margin", "0");
        Paragraph intro = new Paragraph("基于 AgentScope + Spring Boot + Vaadin 的综合性 Agent 管理平台。");
        intro.getStyle().set("margin-top", "0").set("color", "var(--lumo-secondary-text-color)");

        add(title, intro, statCards(modelConfigService, agentInfoService, toolService, mcpServerService));

        Div panels = new Div(trendPanel(chatRecordService.weeklyTrend()),
                activePanel(chatRecordService.topActiveAgents()));
        styleFlexRow(panels);
        panels.getStyle().set("align-items", "stretch");
        add(panels);
    }

    // ---------- 顶部统计卡片 ----------

    private Div statCards(ModelConfigService modelConfigService, AgentInfoService agentInfoService,
                          ToolService toolService, McpServerService mcpServerService) {
        Div cards = new Div(
                statCard("模型数量", modelConfigService.count(), VaadinIcon.DATABASE, BLUE, "models"),
                statCard("智能体数量", agentInfoService.count(), VaadinIcon.CLUSTER, GREEN, "agents"),
                statCard("工具数量", toolService.listTools().size(), VaadinIcon.TOOLS, ORANGE, "tools"),
                statCard("MCP服务数量", mcpServerService.count(), VaadinIcon.PLUG, PURPLE, "mcp"));
        styleFlexRow(cards);
        return cards;
    }

    /** 单个统计卡片：图标 + 数值 + 名称，点击跳转对应管理页 */
    private Div statCard(String label, long value, VaadinIcon vaadinIcon, String[] colors, String route) {
        Icon icon = vaadinIcon.create();
        icon.getStyle()
                .set("width", "40px").set("height", "40px")
                .set("padding", "8px").set("box-sizing", "border-box")
                .set("border-radius", "50%").set("flex-shrink", "0")
                .set("color", colors[0]).set("background", colors[1]);

        Div number = new Div();
        number.setText(String.valueOf(value));
        number.getStyle().set("font-size", "var(--lumo-font-size-xxl)")
                .set("font-weight", "700").set("line-height", "1.2");
        Div text = new Div();
        text.setText(label);
        text.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        Div card = new Div(icon, new Div(number, text));
        card.getStyle()
                .set("flex", "1").set("min-width", "200px")
                .set("display", "flex").set("align-items", "center")
                .set("gap", "var(--lumo-space-m)")
                .set("background", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-left", "4px solid " + colors[0])
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("padding", "var(--lumo-space-m) var(--lumo-space-l)")
                .set("box-sizing", "border-box")
                .set("cursor", "pointer");
        card.addClickListener(e -> getUI().ifPresent(ui -> ui.navigate(route)));
        return card;
    }

    // ---------- 近 7 日对话趋势 ----------

    private Div trendPanel(List<DailyCount> trend) {
        Div panel = panel("近 7 日对话趋势");
        long max = trend.stream().mapToLong(DailyCount::getCount).max().orElse(0);
        LocalDate today = LocalDate.now();

        Div chart = new Div();
        chart.getStyle().set("display", "flex").set("gap", "var(--lumo-space-s)").set("height", "180px");
        for (DailyCount day : trend) {
            Span count = text(String.valueOf(day.getCount()),
                    "var(--lumo-font-size-xs)", "var(--lumo-secondary-text-color)");

            Div bar = new Div();
            // 高度按比例，有数据时至少露出一小节，今日高亮
            int percent = max == 0 ? 0 : (int) Math.round(day.getCount() * 100.0 / max);
            bar.getStyle()
                    .set("width", "60%").set("max-width", "42px")
                    .set("height", Math.max(percent, day.getCount() > 0 ? 4 : 0) + "%")
                    .set("background", day.getDate().equals(today)
                            ? "var(--lumo-primary-color)" : "var(--lumo-primary-color-50pct)")
                    .set("border-radius", "4px 4px 0 0");
            Div barWrap = new Div(bar);
            barWrap.getStyle()
                    .set("flex", "1").set("width", "100%")
                    .set("display", "flex").set("align-items", "flex-end").set("justify-content", "center");

            Span label = text(day.getDate().format(DAY_FORMAT),
                    "var(--lumo-font-size-xs)", "var(--lumo-secondary-text-color)");
            label.getStyle().set("margin-top", "var(--lumo-space-xs)");

            Div column = new Div(count, barWrap, label);
            column.getStyle().set("flex", "1").set("display", "flex")
                    .set("flex-direction", "column").set("align-items", "center");
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
            empty.getStyle().set("color", "var(--lumo-secondary-text-color)")
                    .set("text-align", "center").set("padding", "var(--lumo-space-l) 0");
            panel.add(empty);
            return panel;
        }
        for (int i = 0; i < stats.size(); i++) {
            panel.add(activeRow(stats.get(i), i == stats.size() - 1));
        }
        return panel;
    }

    /** 活跃智能体行：头像 + 名称与指标 + 成功率与最近活跃时间 */
    private Div activeRow(AgentActivityStat stat, boolean last) {
        Avatar avatar = new Avatar(stat.getAgentName());
        avatar.setColorIndex((int) (stat.getAgentId() % 7));

        Span name = new Span(stat.getAgentName());
        name.getStyle().set("font-weight", "600");
        Span metrics = text("近7天 " + stat.getWeekCount() + " 轮 · 共 " + stat.getTotalCount() + " 轮 · "
                        + stat.getSessionCount() + " 个会话 · 工具 " + stat.getToolCallCount() + " 次",
                "var(--lumo-font-size-xs)", "var(--lumo-secondary-text-color)");
        Div middle = new Div(name, metrics);
        middle.getStyle().set("flex", "1").set("min-width", "0")
                .set("display", "flex").set("flex-direction", "column");

        Span rate = text("成功率 " + stat.getSuccessRate() + "%",
                "var(--lumo-font-size-s)", "var(--lumo-success-text-color)");
        rate.getStyle().set("font-weight", "600");
        Span time = text(relativeTime(stat.getLastActiveTime()),
                "var(--lumo-font-size-xs)", "var(--lumo-tertiary-text-color)");
        Div right = new Div(rate, time);
        right.getStyle().set("display", "flex").set("flex-direction", "column")
                .set("align-items", "flex-end").set("flex-shrink", "0");

        Div row = new Div(avatar, middle, right);
        row.getStyle()
                .set("display", "flex").set("align-items", "center")
                .set("gap", "var(--lumo-space-m)")
                .set("padding", "var(--lumo-space-s) 0");
        if (!last) {
            row.getStyle().set("border-bottom", "1px solid var(--lumo-contrast-10pct)");
        }
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

    // ---------- 样式小工具 ----------

    /** 面板容器：白底圆角边框 */
    private Div panel(String title) {
        H3 heading = new H3(title);
        heading.getStyle().set("margin-top", "0").set("font-size", "var(--lumo-font-size-l)");
        Div panel = new Div(heading);
        panel.getStyle()
                .set("flex", "1").set("min-width", "340px")
                .set("background", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("padding", "var(--lumo-space-l)")
                .set("box-sizing", "border-box");
        return panel;
    }

    /** 横向 flex 布局：自动换行、标准间距 */
    private static void styleFlexRow(Div div) {
        div.getStyle()
                .set("display", "flex").set("flex-wrap", "wrap")
                .set("gap", "var(--lumo-space-m)").set("width", "100%");
    }

    private static Span text(String content, String fontSize, String color) {
        Span span = new Span(content);
        span.getStyle().set("font-size", fontSize).set("color", color);
        return span;
    }
}
