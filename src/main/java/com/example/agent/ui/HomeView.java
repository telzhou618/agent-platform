package com.example.agent.ui;

import com.example.agent.system.auth.LoginHelper;
import com.example.agent.system.auth.LoginUser;
import com.example.agent.system.dto.AgentActivityStat;
import com.example.agent.system.dto.ChatOverviewStat;
import com.example.agent.system.dto.DailyCount;
import com.example.agent.system.service.AgentInfoService;
import com.example.agent.system.service.ApiKeyService;
import com.example.agent.system.service.ChatRecordService;
import com.example.agent.system.service.CustomToolService;
import com.example.agent.system.service.KnowledgeBaseService;
import com.example.agent.system.service.McpServerService;
import com.example.agent.system.service.ModelConfigService;
import com.example.agent.system.service.SkillRepoService;
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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 首页 dashboard：资源统计卡片 + 对话数据概览 + 对话趋势 / 活跃智能体 + 模型可用状态 / 快捷入口。
 * 统计数据来自 chat_record 埋点，各资源数量由租户插件按当前用户自动过滤。样式见 styles/home.css。
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("首页 - agent-platform")
@StyleSheet("context://styles/home.css")
public class HomeView extends VerticalLayout {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINESE);

    public HomeView(ModelConfigService modelConfigService, AgentInfoService agentInfoService,
                    KnowledgeBaseService knowledgeBaseService, SkillRepoService skillRepoService,
                    ToolService toolService, CustomToolService customToolService,
                    McpServerService mcpServerService, ApiKeyService apiKeyService,
                    ChatRecordService chatRecordService) {

        add(header());

        add(statCards(modelConfigService, agentInfoService, knowledgeBaseService, skillRepoService,
                toolService, customToolService, mcpServerService, apiKeyService));

        add(overviewPanel(chatRecordService.overview()));

        Div panels = new Div(trendPanel(chatRecordService.weeklyTrend()),
                activePanel(chatRecordService.topActiveAgents()));
        panels.addClassNames("home-flex-row", "home-panels");
        add(panels);

        Div bottom = new Div(modelStatusPanel(modelConfigService), quickLinkPanel());
        bottom.addClassNames("home-flex-row", "home-panels");
        add(bottom);
    }

    // ---------- 顶部问候 ----------

    private Div header() {
        LoginUser user = LoginHelper.currentUser();
        String name = user != null ? user.getUsername() : "";
        H2 title = new H2(greeting() + (name.isEmpty() ? "" : "，" + name));
        title.addClassName("home-title");
        Span date = span(LocalDate.now().format(DATE_FORMAT), "home-date");
        Div titleRow = new Div(title, date);
        titleRow.addClassName("home-title-row");
        Div header = new Div(titleRow);
        header.addClassName("home-header");
        return header;
    }

    private static String greeting() {
        int hour = LocalTime.now().getHour();
        if (hour < 6) {
            return "夜深了";
        }
        if (hour < 12) {
            return "早上好";
        }
        if (hour < 14) {
            return "中午好";
        }
        if (hour < 18) {
            return "下午好";
        }
        return "晚上好";
    }

    // ---------- 资源统计卡片 ----------

    private Div statCards(ModelConfigService modelConfigService, AgentInfoService agentInfoService,
                          KnowledgeBaseService knowledgeBaseService, SkillRepoService skillRepoService,
                          ToolService toolService, CustomToolService customToolService,
                          McpServerService mcpServerService, ApiKeyService apiKeyService) {
        Div cards = new Div(
                statCard("模型", modelConfigService.count(), VaadinIcon.DATABASE, "blue", "models"),
                statCard("智能体", agentInfoService.count(), VaadinIcon.CLUSTER, "green", "agents"),
                statCard("知识库", knowledgeBaseService.count(), VaadinIcon.BOOK, "teal", "knowledge"),
                statCard("技能仓库", skillRepoService.count(), VaadinIcon.LIGHTBULB, "yellow", "skills"),
                statCard("系统工具", toolService.listTools().size(), VaadinIcon.TOOLS, "orange", "tools"),
                statCard("自定义工具", customToolService.count(), VaadinIcon.EXTERNAL_LINK, "pink", "custom-tools"),
                statCard("MCP服务", mcpServerService.count(), VaadinIcon.PLUG, "purple", "mcp"),
                statCard("ApiKey", apiKeyService.count(), VaadinIcon.KEY, "red", "apikey"));
        cards.addClassName("stat-cards");
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

    // ---------- 对话数据概览 ----------

    private Div overviewPanel(ChatOverviewStat stat) {
        Div panel = panel("对话数据概览");
        Long total = stat.getTotalCount();
        boolean empty = total == null || total == 0;

        Div strip = new Div(
                miniStat("今日对话", empty ? "0" : String.valueOf(stat.getTodayCount())),
                miniStat("累计对话", empty ? "0" : String.valueOf(total)),
                miniStat("会话总数", empty ? "0" : String.valueOf(stat.getSessionCount())),
                miniStat("工具调用", empty ? "0" : String.valueOf(stat.getToolCallCount())),
                miniStat("平均耗时", empty ? "—" : formatDuration(stat.getAvgDurationMs())),
                miniStat("成功率", empty ? "—" : stat.getSuccessRate() + "%",
                        empty ? "" : rateClass(stat.getSuccessRate())));
        strip.addClassName("overview-strip");
        panel.add(strip);
        return panel;
    }

    /**
     * 概览条上的单个指标：大数值 + 小标签；extraClass 用于成功率按区间着色
     */
    private Div miniStat(String label, String value, String... extraClass) {
        Div number = new Div();
        number.setText(value);
        number.addClassName("overview-value");
        for (String cls : extraClass) {
            if (!cls.isEmpty()) {
                number.addClassName(cls);
            }
        }
        Div text = new Div();
        text.setText(label);
        text.addClassName("overview-label");
        Div item = new Div(number, text);
        item.addClassName("overview-item");
        return item;
    }

    /**
     * 成功率配色：>=90 绿，>=70 橙，其余红
     */
    private static String rateClass(long rate) {
        if (rate >= 90) {
            return "overview-value-good";
        }
        if (rate >= 70) {
            return "overview-value-warn";
        }
        return "overview-value-bad";
    }

    private static String formatDuration(Long ms) {
        if (ms == null) {
            return "—";
        }
        if (ms < 1000) {
            return ms + "ms";
        }
        return String.format("%.1fs", ms / 1000.0);
    }

    // ---------- 近 7 日对话趋势 ----------

    private Div trendPanel(List<DailyCount> trend) {
        Div panel = panel("近 7 日对话趋势");
        panel.addClassName("panel-trend");
        long max = trend.stream().mapToLong(DailyCount::getCount).max().orElse(0);
        LocalDate today = LocalDate.now();

        Div chart = new Div();
        chart.addClassName("trend-chart");
        for (DailyCount day : trend) {
            Span count = span(String.valueOf(day.getCount()), "trend-count");

            Div bar = new Div();
            // 高度按比例，有数据时至少露出一小节，今日高亮；title 提供悬停提示
            int percent = max == 0 ? 0 : (int) Math.round(day.getCount() * 100.0 / max);
            bar.addClassName("trend-bar");
            if (day.getDate().equals(today)) {
                bar.addClassName("trend-bar-today");
            }
            bar.getStyle().set("height", Math.max(percent, day.getCount() > 0 ? 4 : 0) + "%");
            bar.setTitle(day.getDate().format(DAY_FORMAT) + "：" + day.getCount() + " 轮对话");
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
        panel.addClassName("panel-active");
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

    // ---------- 模型可用状态 ----------

    private Div modelStatusPanel(ModelConfigService modelConfigService) {
        Div panel = panel("模型可用状态");
        panel.addClassName("panel-model");
        long total = modelConfigService.count();
        if (total == 0) {
            Div empty = new Div();
            empty.setText("还没有配置模型，去添加一个吧");
            empty.addClassName("active-empty");
            Div links = new Div(linkButton("添加模型", VaadinIcon.PLUS, "models"));
            links.addClassName("quick-links");
            panel.add(empty, links);
            return panel;
        }

        long[] summary = modelConfigService.availabilitySummary();
        long available = summary[0];
        long unavailable = summary[1];

        // 左侧大数字：可用 / 总数
        Div ratio = new Div();
        ratio.setText(available + "/" + total);
        ratio.addClassName("model-status-ratio");
        Div ratioBox = new Div(ratio, span("模型可用", "model-status-ratio-label"));
        ratioBox.addClassName("model-status-ratio-box");

        // 右侧：可用占比进度条 + 明细数字
        Div fill = new Div();
        fill.addClassName("model-status-fill");
        fill.getStyle().set("width", Math.round(available * 100.0 / total) + "%");
        Div bar = new Div(fill);
        bar.addClassName("model-status-bar");
        Span numbers = span("共 " + total + " 个模型 · 可用 " + available + " 个 · 不可用 " + unavailable + " 个",
                "model-status-numbers");
        Div barBlock = new Div(bar, numbers);
        barBlock.addClassName("model-status-bar-block");

        Div body = new Div(ratioBox, barBlock);
        body.addClassName("model-status-body");
        panel.add(body);

        if (unavailable > 0) {
            Span warn = span(unavailable + " 个模型不可用，建议前往模型管理重新检测", "model-status-warn");
            Div links = new Div(linkButton("去处理", VaadinIcon.WRENCH, "models"));
            links.addClassName("quick-links");
            panel.add(warn, links);
        }
        return panel;
    }

    // ---------- 快捷入口 ----------

    private Div quickLinkPanel() {
        Div panel = panel("快捷入口");
        panel.addClassName("panel-quick");
        Div links = new Div(
                linkButton("开始对话", VaadinIcon.CHAT, "chat"),
                linkButton("新建智能体", VaadinIcon.PLUS, "agents"),
                linkButton("添加模型", VaadinIcon.DATABASE, "models"),
                linkButton("配置知识库", VaadinIcon.BOOK, "knowledge"));
        links.addClassName("quick-links");
        panel.add(links);
        return panel;
    }

    /**
     * 快捷入口按钮：图标 + 文字，整块可点击
     */
    private Div linkButton(String label, VaadinIcon vaadinIcon, String route) {
        Icon icon = vaadinIcon.create();
        icon.addClassName("quick-link-icon");
        Span text = span(label, "quick-link-label");
        Div link = new Div(icon, text);
        link.addClassName("quick-link");
        link.addClickListener(e -> getUI().ifPresent(ui -> ui.navigate(route)));
        return link;
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
