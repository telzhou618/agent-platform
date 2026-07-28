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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 数据看板：渐变 Hero + 资源统计 + 对话趋势 / 对话概览 + 活跃排行 / 模型可用环图 + 快捷入口。
 * 与旧首页 HomeView 并存（路由 /dashboard），统计数据同样来自 chat_record 埋点，样式见 styles/dashboard.css。
 */
@Route(value = "dashboard", layout = MainLayout.class)
@PageTitle("数据看板 - agent-platform")
@StyleSheet("context://styles/dashboard.css")
public class DashboardView extends VerticalLayout {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINESE);
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public DashboardView(ModelConfigService modelConfigService, AgentInfoService agentInfoService,
                         KnowledgeBaseService knowledgeBaseService, SkillRepoService skillRepoService,
                         ToolService toolService, CustomToolService customToolService,
                         McpServerService mcpServerService, ApiKeyService apiKeyService,
                         ChatRecordService chatRecordService) {
        addClassName("dash-view");

        ChatOverviewStat overview = chatRecordService.overview();
        long[] modelSummary = modelConfigService.availabilitySummary();

        add(hero(overview, modelSummary[0]));

        add(statCards(modelConfigService, agentInfoService, knowledgeBaseService, skillRepoService,
                toolService, customToolService, mcpServerService, apiKeyService));

        Div middle = new Div(trendPanel(chatRecordService.weeklyTrend()), overviewPanel(overview));
        middle.addClassNames("dash-row", "dash-row-middle");
        add(middle);

        Div bottom = new Div(rankPanel(chatRecordService.topActiveAgents()), modelPanel(modelConfigService));
        bottom.addClassNames("dash-row", "dash-row-bottom");
        add(bottom);

        add(quickLinkPanel());
        add(footnote());
    }

    // ---------- 顶部 Hero：问候 + 平台摘要 + 主操作 ----------

    private Div hero(ChatOverviewStat overview, long modelAvailable) {
        LoginUser user = LoginHelper.currentUser();
        String name = user != null ? user.getUsername() : "";

        H2 title = new H2(greeting() + (name.isEmpty() ? "" : "，" + name));
        title.addClassName("dash-hero-title");
        Span date = span(LocalDate.now().format(DATE_FORMAT), "dash-hero-date");

        // 平台摘要玻璃片：一句话说清当前规模
        Div chips = new Div(
                heroChip("今日对话", num(overview.getTodayCount()) + " 轮"),
                heroChip("累计对话", num(overview.getTotalCount()) + " 轮"),
                heroChip("可用模型", modelAvailable + " 个"));
        chips.addClassName("dash-hero-chips");

        Div text = new Div(title, date, chips);
        text.addClassName("dash-hero-text");

        Button chat = new Button("开始对话", new Icon(VaadinIcon.CHAT),
                e -> getUI().ifPresent(ui -> ui.navigate("chat")));
        chat.addClassNames("dash-hero-btn", "dash-hero-btn-primary");
        Button newAgent = new Button("新建智能体", new Icon(VaadinIcon.PLUS),
                e -> getUI().ifPresent(ui -> ui.navigate("agents")));
        newAgent.addClassName("dash-hero-btn");
        Div actions = new Div(chat, newAgent);
        actions.addClassName("dash-hero-actions");

        Div hero = new Div(text, actions);
        hero.addClassName("dash-hero");
        return hero;
    }

    private Div heroChip(String label, String value) {
        Div chip = new Div(span(value, "dash-hero-chip-value"), span(label, "dash-hero-chip-label"));
        chip.addClassName("dash-hero-chip");
        return chip;
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
        cards.addClassName("dash-stats");
        return cards;
    }

    /**
     * 单个统计卡片：渐变图标 + 数值 + 名称，点击跳转对应管理页；variant 对应 dash-stat-* 主题色变体
     */
    private Div statCard(String label, long value, VaadinIcon vaadinIcon, String variant, String route) {
        Icon icon = vaadinIcon.create();
        icon.addClassName("dash-stat-icon");
        Div number = new Div();
        number.setText(num(value));
        number.addClassName("dash-stat-num");
        Div text = new Div(number, span(label, "dash-stat-label"));
        text.addClassName("dash-stat-text");
        Div card = new Div(icon, text);
        card.addClassNames("dash-stat", "dash-stat-" + variant);
        card.addClickListener(e -> getUI().ifPresent(ui -> ui.navigate(route)));
        return card;
    }

    // ---------- 近 7 日对话趋势 ----------

    private Div trendPanel(List<DailyCount> trend) {
        long total = trend.stream().mapToLong(DailyCount::getCount).sum();
        long max = trend.stream().mapToLong(DailyCount::getCount).max().orElse(0);
        LocalDate today = LocalDate.now();

        Div panel = panel("近 7 日对话趋势", "7 日共 " + num(total) + " 轮", VaadinIcon.CHART_LINE);
        panel.addClassName("dash-panel-trend");

        Div chart = new Div();
        chart.addClassName("dash-trend-chart");
        for (DailyCount day : trend) {
            Span count = span(String.valueOf(day.getCount()), "dash-trend-count");

            // 高度按比例，有数据时至少露出一小节，今日高亮；title 提供悬停提示
            int percent = max == 0 ? 0 : (int) Math.round(day.getCount() * 100.0 / max);
            Div bar = new Div();
            bar.addClassName("dash-trend-bar");
            if (day.getDate().equals(today)) {
                bar.addClassName("dash-trend-bar-today");
            }
            bar.getStyle().set("height", Math.max(percent, day.getCount() > 0 ? 4 : 0) + "%");
            bar.setTitle(day.getDate().format(DAY_FORMAT) + "：" + day.getCount() + " 轮对话");
            Div barWrap = new Div(bar);
            barWrap.addClassName("dash-trend-bar-wrap");

            Span label = span(day.getDate().format(DAY_FORMAT), "dash-trend-label");
            if (day.getDate().equals(today)) {
                label.addClassName("dash-trend-label-today");
            }

            Div column = new Div(count, barWrap, label);
            column.addClassName("dash-trend-column");
            chart.add(column);
        }
        panel.add(chart);
        return panel;
    }

    // ---------- 对话数据概览 ----------

    private Div overviewPanel(ChatOverviewStat stat) {
        Div panel = panel("对话数据概览", "chat_record 实时埋点", VaadinIcon.CLIPBOARD_PULSE);
        panel.addClassName("dash-panel-overview");
        Long total = stat.getTotalCount();
        boolean empty = total == null || total == 0;

        Div grid = new Div(
                kpi("今日对话", empty ? "0" : num(stat.getTodayCount()), VaadinIcon.COMMENTS, "blue", ""),
                kpi("累计对话", empty ? "0" : num(total), VaadinIcon.ARCHIVE, "purple", ""),
                kpi("会话总数", empty ? "0" : num(stat.getSessionCount()), VaadinIcon.FOLDER, "teal", ""),
                kpi("工具调用", empty ? "0" : num(stat.getToolCallCount()), VaadinIcon.TOOLS, "orange", ""),
                kpi("平均耗时", empty ? "—" : formatDuration(stat.getAvgDurationMs()), VaadinIcon.CLOCK, "pink", ""),
                kpi("成功率", empty ? "—" : stat.getSuccessRate() + "%", VaadinIcon.CHECK_CIRCLE, "green",
                        empty ? "" : rateClass(stat.getSuccessRate())));
        grid.addClassName("dash-kpi-grid");
        panel.add(grid);
        return panel;
    }

    /**
     * 概览网格中的单个指标：小图标 + 大数值 + 标签；extraClass 用于成功率按区间着色
     */
    private Div kpi(String label, String value, VaadinIcon vaadinIcon, String variant, String extraClass) {
        Icon icon = vaadinIcon.create();
        icon.addClassNames("dash-kpi-icon", "dash-kpi-icon-" + variant);
        Div number = new Div();
        number.setText(value);
        number.addClassName("dash-kpi-value");
        if (!extraClass.isEmpty()) {
            number.addClassName(extraClass);
        }
        Div text = new Div(number, span(label, "dash-kpi-label"));
        text.addClassName("dash-kpi-text");
        Div item = new Div(icon, text);
        item.addClassName("dash-kpi");
        return item;
    }

    /**
     * 成功率配色：>=90 绿，>=70 橙，其余红
     */
    private static String rateClass(long rate) {
        if (rate >= 90) {
            return "dash-kpi-good";
        }
        if (rate >= 70) {
            return "dash-kpi-warn";
        }
        return "dash-kpi-bad";
    }

    // ---------- 最近活跃智能体排行 ----------

    private Div rankPanel(List<AgentActivityStat> stats) {
        Div panel = panel("最近活跃智能体", "按最近活跃时间排序", VaadinIcon.TROPHY);
        panel.addClassName("dash-panel-rank");
        if (stats.isEmpty()) {
            Div empty = new Div();
            empty.setText("暂无对话记录，去「流式对话」聊聊吧");
            empty.addClassName("dash-empty");
            panel.add(empty);
            return panel;
        }
        Div list = new Div();
        list.addClassName("dash-rank-list");
        int rank = 1;
        for (AgentActivityStat stat : stats) {
            list.add(rankRow(rank++, stat));
        }
        panel.add(list);
        return panel;
    }

    /**
     * 排行行：名次徽标（前三金银铜）+ 头像 + 名称与指标 + 成功率与最近活跃时间
     */
    private Div rankRow(int rank, AgentActivityStat stat) {
        Span no = span(String.valueOf(rank), "dash-rank-no");
        if (rank <= 3) {
            no.addClassName("dash-rank-no-" + rank);
        }

        Avatar avatar = new Avatar(stat.getAgentName());
        avatar.setColorIndex((int) (stat.getAgentId() % 7));

        Span name = span(stat.getAgentName(), "dash-rank-name");
        Span metrics = span("近7天 " + stat.getWeekCount() + " 轮 · 共 " + stat.getTotalCount() + " 轮 · "
                        + stat.getSessionCount() + " 个会话 · 工具 " + stat.getToolCallCount() + " 次",
                "dash-rank-metrics");
        Div middle = new Div(name, metrics);
        middle.addClassName("dash-rank-middle");

        Span rate = span("成功率 " + stat.getSuccessRate() + "%",
                "dash-rank-rate " + rateClass(stat.getSuccessRate()));
        Span time = span(relativeTime(stat.getLastActiveTime()), "dash-rank-time");
        Div right = new Div(rate, time);
        right.addClassName("dash-rank-right");

        Div row = new Div(no, avatar, middle, right);
        row.addClassName("dash-rank-row");
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

    // ---------- 模型可用状态环图 ----------

    private Div modelPanel(ModelConfigService modelConfigService) {
        Div panel = panel("模型可用状态", "保存时真实调用验证", VaadinIcon.CONNECT);
        panel.addClassName("dash-panel-model");
        long total = modelConfigService.count();
        if (total == 0) {
            Div empty = new Div();
            empty.setText("还没有配置模型，去添加一个吧");
            empty.addClassName("dash-empty");
            panel.add(empty, quickLink("添加模型", VaadinIcon.PLUS, "models"));
            return panel;
        }

        long[] summary = modelConfigService.availabilitySummary();
        long available = summary[0];
        long unavailable = summary[1];
        int percent = (int) Math.round(available * 100.0 / total);

        // 环图：conic-gradient 按可用占比填充，角度由内联 CSS 变量 --dash-deg 控制
        Div center = new Div();
        Span ratio = span(percent + "%", "dash-donut-value");
        Span caption = span("可用率", "dash-donut-caption");
        center.add(ratio, caption);
        center.addClassName("dash-donut-center");
        Div donut = new Div(center);
        donut.addClassName("dash-donut");
        donut.getStyle().set("--dash-deg", (percent * 3.6) + "deg");

        // 右侧图例：总数 / 可用 / 不可用
        Div legend = new Div(
                legendItem("gray", "模型总数", num(total)),
                legendItem("green", "可用", num(available)),
                legendItem("red", "不可用", num(unavailable)));
        legend.addClassName("dash-legend");

        Div body = new Div(donut, legend);
        body.addClassName("dash-donut-body");
        panel.add(body);

        if (unavailable > 0) {
            Span warn = span(unavailable + " 个模型不可用，建议前往模型管理重新检测", "dash-model-warn");
            panel.add(warn, quickLink("去处理", VaadinIcon.WRENCH, "models"));
        }
        return panel;
    }

    private Div legendItem(String variant, String label, String value) {
        Span dot = span("", "dash-legend-dot dash-legend-dot-" + variant);
        Div item = new Div(dot, span(label, "dash-legend-label"), span(value, "dash-legend-value"));
        item.addClassName("dash-legend-item");
        return item;
    }

    // ---------- 快捷入口 ----------

    private Div quickLinkPanel() {
        Div panel = panel("快捷入口", "高频操作一键直达", VaadinIcon.FLASH);
        panel.addClassName("dash-panel-links");
        Div links = new Div(
                quickLink("开始对话", VaadinIcon.CHAT, "chat"),
                quickLink("新建智能体", VaadinIcon.PLUS, "agents"),
                quickLink("添加模型", VaadinIcon.DATABASE, "models"),
                quickLink("配置知识库", VaadinIcon.BOOK, "knowledge"),
                quickLink("浏览技能", VaadinIcon.LIGHTBULB, "skills"),
                quickLink("接入MCP", VaadinIcon.PLUG, "mcp"));
        links.addClassName("dash-links");
        panel.add(links);
        return panel;
    }

    /**
     * 快捷入口磁贴：图标在上、文字在下，整块可点击
     */
    private Div quickLink(String label, VaadinIcon vaadinIcon, String route) {
        Icon icon = vaadinIcon.create();
        icon.addClassName("dash-link-icon");
        Div link = new Div(icon, span(label, "dash-link-label"));
        link.addClassName("dash-link");
        link.addClickListener(e -> getUI().ifPresent(ui -> ui.navigate(route)));
        return link;
    }

    // ---------- 底部数据说明 ----------

    private Div footnote() {
        Div note = new Div();
        note.setText("数据更新于 " + LocalTime.now().format(CLOCK_FORMAT) + " · 统计口径：当前登录用户可见资源");
        note.addClassName("dash-footnote");
        return note;
    }

    // ---------- 组件小工具 ----------

    /**
     * 面板容器：图标标题 + 副标题 + 白底圆角卡片
     */
    private Div panel(String title, String subtitle, VaadinIcon vaadinIcon) {
        Icon icon = vaadinIcon.create();
        icon.addClassName("dash-panel-icon");
        H3 heading = new H3(title);
        heading.addClassName("dash-panel-title");
        Div headLeft = new Div(icon, heading);
        headLeft.addClassName("dash-panel-head-left");
        Div head = new Div(headLeft, span(subtitle, "dash-panel-sub"));
        head.addClassName("dash-panel-head");
        Div panel = new Div(head);
        panel.addClassName("dash-panel");
        return panel;
    }

    private static Span span(String content, String classNames) {
        Span span = new Span(content);
        span.addClassNames(classNames.split(" "));
        return span;
    }

    private static String num(Long value) {
        return value == null ? "0" : NumberFormat.getInstance().format(value);
    }

    private static String num(long value) {
        return NumberFormat.getInstance().format(value);
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
}
