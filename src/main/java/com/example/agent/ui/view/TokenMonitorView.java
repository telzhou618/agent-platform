package com.example.agent.ui.view;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.system.dto.AgentTokenStat;
import com.example.agent.system.dto.DailyTokenUsage;
import com.example.agent.system.dto.TokenOverviewStat;
import com.example.agent.system.entity.AgentInfo;
import com.example.agent.system.entity.AgentTokenUsage;
import com.example.agent.system.service.AgentInfoService;
import com.example.agent.system.service.TokenUsageService;
import com.example.agent.ui.MainLayout;
import com.example.agent.ui.component.PaginationBar;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.ComboBoxVariant;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.select.SelectVariant;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Token 监控：KPI 指标卡 + 消耗趋势（纯 CSS 堆叠柱图）+ 智能体消耗排行 + 消耗明细分页。
 * 数据来自 agent_token_usage 埋点（每次模型调用一条），租户过滤由服务层处理。样式见 styles/token-monitor.css。
 */
@Route(value = "token-monitor", layout = MainLayout.class)
@PageTitle("Token监控 - agent-platform")
@StyleSheet("context://styles/token-monitor.css")
public class TokenMonitorView extends VerticalLayout {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 来源过滤选项：值 -> 展示文案，空值表示不过滤 */
    private static final Map<String, String> SOURCES = new LinkedHashMap<>();

    static {
        SOURCES.put("", "全部来源");
        SOURCES.put(AgentTokenUsage.SOURCE_ADMIN, "管理端");
        SOURCES.put(AgentTokenUsage.SOURCE_API, "开放接口");
    }

    private final TokenUsageService tokenUsageService;
    /** 智能体 id -> 名称，明细表格与过滤器共用，查不到的显示 #id */
    private final Map<Long, String> agentNames;

    private final Grid<AgentTokenUsage> grid = new Grid<>(AgentTokenUsage.class, false);
    private final ComboBox<AgentInfo> agentFilter = new ComboBox<>();
    private final Select<String> sourceFilter = new Select<>();
    private final PaginationBar paginationBar = new PaginationBar(this::loadPage);

    private final Div trendChart = new Div();
    private final Span trendTotal = new Span();
    private final Button trend7 = new Button("7 天", e -> switchTrend(7));
    private final Button trend30 = new Button("30 天", e -> switchTrend(30));
    private int trendDays = 7;

    public TokenMonitorView(TokenUsageService tokenUsageService, AgentInfoService agentInfoService) {
        this.tokenUsageService = tokenUsageService;
        addClassName("tm-view");

        List<AgentInfo> agents = agentInfoService.list();
        agentNames = agents.stream()
                .collect(Collectors.toMap(AgentInfo::getId, AgentInfo::getName, (a, b) -> a));

        add(kpiRow(tokenUsageService.overview()));
        add(trendPanel());
        add(rankPanel(tokenUsageService.agentStats()));
        add(detailPanel(agents));
        paginationBar.refresh();
    }

    // ---------- 顶部 KPI 指标卡 ----------

    private Div kpiRow(TokenOverviewStat stat) {
        long rate = stat.getCacheHitRate();
        Div cards = new Div(
                kpi("今日 Token", abbrev(stat.getTodayTokens()),
                        "累计输入 " + abbrev(stat.getInputTokens()) + " · 输出 " + abbrev(stat.getOutputTokens()),
                        VaadinIcon.COINS, "blue", ""),
                kpi("累计 Token", abbrev(stat.getTotalTokens()),
                        "缓存命中 " + abbrev(stat.getCachedTokens()),
                        VaadinIcon.ARCHIVE, "purple", ""),
                kpi("今日调用", num(stat.getTodayCalls()),
                        "累计 " + num(stat.getTotalCalls()) + " 次",
                        VaadinIcon.BOLT, "orange", ""),
                kpi("缓存命中率", rate == 0 ? "—" : rate + "%",
                        "缓存 " + abbrev(stat.getCachedTokens()) + " / 输入 " + abbrev(stat.getInputTokens()),
                        VaadinIcon.STORAGE, "green", rateClass(rate)),
                kpi("平均耗时", formatDuration(stat.getAvgDurationMs()),
                        "单次模型调用",
                        VaadinIcon.TIMER, "pink", ""),
                kpi("活跃智能体", num(stat.getAgentCount()),
                        "产生消耗的智能体数",
                        VaadinIcon.CLUSTER, "teal", ""));
        cards.addClassName("tm-kpi-grid");
        return cards;
    }

    /**
     * 单个指标卡：渐变图标 + 大数值 + 标签 + 补充小字；extraClass 用于命中率按区间着色
     */
    private Div kpi(String label, String value, String sub, VaadinIcon vaadinIcon, String variant, String extraClass) {
        Icon icon = vaadinIcon.create();
        icon.addClassName("tm-kpi-icon");
        Div number = new Div();
        number.setText(value);
        number.addClassName("tm-kpi-value");
        if (!extraClass.isEmpty()) {
            number.addClassName(extraClass);
        }
        Div text = new Div(number, span(label, "tm-kpi-label"), span(sub, "tm-kpi-sub"));
        text.addClassName("tm-kpi-text");
        Div card = new Div(icon, text);
        card.addClassNames("tm-kpi", "tm-kpi-" + variant);
        return card;
    }

    /**
     * 缓存命中率配色：>=30 绿，10-30 橙，<10 灰
     */
    private static String rateClass(long rate) {
        if (rate >= 30) {
            return "tm-rate-good";
        }
        if (rate >= 10) {
            return "tm-rate-warn";
        }
        return "tm-rate-muted";
    }

    // ---------- Token 消耗趋势（7 天 / 30 天切换） ----------

    private Div trendPanel() {
        trend7.addClassName("tm-toggle-btn");
        trend30.addClassName("tm-toggle-btn");
        Div toggle = new Div(trend7, trend30);
        toggle.addClassName("tm-toggle");
        Div panel = panel("Token 消耗趋势", toggle, VaadinIcon.CHART_LINE);

        trendTotal.addClassName("tm-trend-total");
        Div legend = new Div(legendItem("in", "输入 token"), legendItem("out", "输出 token"));
        legend.addClassName("tm-trend-legend");
        Div meta = new Div(trendTotal, legend);
        meta.addClassName("tm-trend-meta");

        trendChart.addClassName("tm-trend-chart");
        panel.add(meta, trendChart);
        updateToggle();
        rebuildTrend();
        return panel;
    }

    private Div legendItem(String variant, String label) {
        Div item = new Div(span("", "tm-legend-dot tm-legend-dot-" + variant), new Span(label));
        item.addClassName("tm-legend-item");
        return item;
    }

    private void switchTrend(int days) {
        if (days == trendDays) {
            return;
        }
        trendDays = days;
        updateToggle();
        rebuildTrend();
    }

    /**
     * 切换按钮高亮状态
     */
    private void updateToggle() {
        trend7.setClassName("tm-toggle-active", trendDays == 7);
        trend30.setClassName("tm-toggle-active", trendDays == 30);
    }

    /**
     * 重建堆叠柱图：每天一柱，输入段（主色）在下、输出段在上；全零时显示空态
     */
    private void rebuildTrend() {
        trendChart.removeAll();
        List<DailyTokenUsage> trend = tokenUsageService.trend(trendDays);
        long total = trend.stream().mapToLong(DailyTokenUsage::getTotalTokens).sum();
        long max = trend.stream().mapToLong(DailyTokenUsage::getTotalTokens).max().orElse(0);
        trendTotal.setText("近 " + trendDays + " 天共 " + abbrev(total) + " token");
        if (max == 0) {
            Div empty = new Div();
            empty.setText("暂无 token 消耗记录，对话后自动统计");
            empty.addClassName("tm-empty");
            trendChart.add(empty);
            return;
        }
        LocalDate today = LocalDate.now();
        for (DailyTokenUsage day : trend) {
            trendChart.add(trendColumn(day, max, today));
        }
    }

    private Div trendColumn(DailyTokenUsage day, long max, LocalDate today) {
        long dayTotal = day.getTotalTokens();
        long in = day.getInputTokens() == null ? 0 : day.getInputTokens();
        long out = day.getOutputTokens() == null ? 0 : day.getOutputTokens();

        // 柱高按最大值归一（最大值=100%），有数据至少露出 4%；输入/输出两段按各自占比切分
        int totalPct = (int) Math.round(dayTotal * 100.0 / max);
        if (dayTotal > 0) {
            totalPct = Math.max(4, totalPct);
        }
        int inPct = dayTotal == 0 ? 0 : (int) Math.round(in * (double) totalPct / dayTotal);
        if (in > 0 && inPct == 0) {
            inPct = 1;
        }
        int outPct = totalPct - inPct;
        if (out > 0 && outPct == 0) {
            outPct = 1;
            inPct = Math.max(0, totalPct - 1);
        }

        Div segOut = new Div();
        segOut.addClassName("tm-trend-seg-out");
        segOut.getStyle().set("height", outPct + "%");
        Div segIn = new Div();
        segIn.addClassName("tm-trend-seg-in");
        segIn.getStyle().set("height", inPct + "%");

        Div bar = new Div(segOut, segIn);
        bar.addClassName("tm-trend-bar");
        bar.getStyle().set("height", totalPct + "%");
        bar.setTitle(day.getDate().format(DAY_FORMAT) + "：输入 " + num(in) + " · 输出 " + num(out)
                + " · 共 " + num(dayTotal));
        if (day.getDate().equals(today)) {
            bar.addClassName("tm-trend-bar-today");
        }
        Div barWrap = new Div(bar);
        barWrap.addClassName("tm-trend-bar-wrap");

        Span label = span(day.getDate().format(DAY_FORMAT), "tm-trend-label");
        if (day.getDate().equals(today)) {
            label.addClassName("tm-trend-label-today");
        }

        Div column = new Div(span(abbrev(dayTotal), "tm-trend-count"), barWrap, label);
        column.addClassName("tm-trend-column");
        return column;
    }

    // ---------- 智能体消耗排行 ----------

    private Div rankPanel(List<AgentTokenStat> stats) {
        Div panel = panel("智能体消耗排行", span("按累计 token 消耗倒序", "tm-panel-sub"), VaadinIcon.TROPHY);
        if (stats.isEmpty()) {
            Div empty = new Div();
            empty.setText("暂无数据");
            empty.addClassName("tm-empty");
            panel.add(empty);
            return panel;
        }
        long max = stats.stream().mapToLong(s -> s.getTotalTokens() == null ? 0 : s.getTotalTokens())
                .max().orElse(0);
        Div list = new Div();
        list.addClassName("tm-rank-list");
        int rank = 1;
        for (AgentTokenStat stat : stats) {
            list.add(rankRow(rank++, stat, max));
        }
        panel.add(list);
        return panel;
    }

    /**
     * 排行行：名次徽标（前三金银铜）+ 头像 + 名称/模型与占比进度条 + 累计 token 与调用指标
     */
    private Div rankRow(int rank, AgentTokenStat stat, long max) {
        Span no = span(String.valueOf(rank), "tm-rank-no");
        if (rank <= 3) {
            no.addClassName("tm-rank-no-" + rank);
        }

        String name = stat.getAgentName() == null || stat.getAgentName().isBlank()
                ? "#" + stat.getAgentId() : stat.getAgentName();
        Avatar avatar = new Avatar(name);
        avatar.setColorIndex((int) (stat.getAgentId() % 7));

        Span nameSpan = span(name, "tm-rank-name");
        Span model = span(stat.getModelName() == null ? "未配置模型" : stat.getModelName(), "tm-rank-model");
        Div head = new Div(nameSpan, model);
        head.addClassName("tm-rank-head");

        // 占比进度条：该行 totalTokens / 全部行最大 totalTokens，有数据至少露出 3%
        long tokens = stat.getTotalTokens() == null ? 0 : stat.getTotalTokens();
        int width = max == 0 ? 0 : (int) Math.round(tokens * 100.0 / max);
        if (tokens > 0) {
            width = Math.max(3, width);
        }
        Div fill = new Div();
        fill.addClassName("tm-rank-bar-fill");
        fill.getStyle().set("width", width + "%");
        Div bar = new Div(fill);
        bar.addClassName("tm-rank-bar");

        Div middle = new Div(head, bar);
        middle.addClassName("tm-rank-middle");

        Span tokenNum = span(abbrev(tokens), "tm-rank-tokens");
        Span metrics = span(num(stat.getCallCount()) + " 次调用 · 缓存 " + stat.getCacheHitRate() + "% · 平均 "
                + formatDuration(stat.getAvgDurationMs()) + " · 最后活跃 " + relativeTime(stat.getLastActiveTime()),
                "tm-rank-metrics");
        Div right = new Div(tokenNum, metrics);
        right.addClassName("tm-rank-right");

        Div row = new Div(no, avatar, middle, right);
        row.addClassName("tm-rank-row");
        return row;
    }

    private static String relativeTime(LocalDateTime time) {
        if (time == null) {
            return "—";
        }
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
        return time.format(DATE_FORMAT);
    }

    // ---------- 消耗明细（过滤器 + 分页表格） ----------

    private Div detailPanel(List<AgentInfo> agents) {
        agentFilter.setItems(agents);
        agentFilter.setItemLabelGenerator(AgentInfo::getName);
        agentFilter.setPlaceholder("全部智能体");
        agentFilter.setClearButtonVisible(true);
        agentFilter.setWidth("190px");
        agentFilter.addThemeVariants(ComboBoxVariant.LUMO_SMALL);
        agentFilter.addValueChangeListener(e -> paginationBar.reset());

        sourceFilter.setItems("", AgentTokenUsage.SOURCE_ADMIN, AgentTokenUsage.SOURCE_API);
        sourceFilter.setItemLabelGenerator(SOURCES::get);
        sourceFilter.setValue("");
        sourceFilter.setWidth("130px");
        sourceFilter.addThemeVariants(SelectVariant.LUMO_SMALL);
        sourceFilter.addValueChangeListener(e -> paginationBar.reset());

        Div filters = new Div(agentFilter, sourceFilter);
        filters.addClassName("tm-detail-filters");
        Div panel = panel("消耗明细", filters, VaadinIcon.CLIPBOARD_TEXT);

        grid.addColumn(r -> r.getCreateTime() == null ? "-" : r.getCreateTime().format(DATETIME_FORMAT))
                .setHeader("时间").setWidth("190px").setFlexGrow(0);
        grid.addColumn(r -> agentNames.getOrDefault(r.getAgentId(), "#" + r.getAgentId()))
                .setHeader("智能体").setWidth("150px").setFlexGrow(0);
        grid.addColumn(r -> r.getModelName() == null ? "-" : r.getModelName()).setHeader("模型");
        grid.addComponentColumn(this::sourceBadge).setHeader("来源").setWidth("100px").setFlexGrow(0);
        grid.addColumn(r -> intNum(r.getInputTokens())).setHeader("输入")
                .setTextAlign(ColumnTextAlign.END).setWidth("100px").setFlexGrow(0);
        grid.addColumn(r -> intNum(r.getOutputTokens())).setHeader("输出")
                .setTextAlign(ColumnTextAlign.END).setWidth("100px").setFlexGrow(0);
        grid.addColumn(r -> intNum(r.getCachedTokens())).setHeader("缓存")
                .setTextAlign(ColumnTextAlign.END).setWidth("100px").setFlexGrow(0);
        grid.addColumn(r -> intNum(r.getTotalTokens())).setHeader("总计")
                .setTextAlign(ColumnTextAlign.END).setClassNameGenerator(r -> "tm-total")
                .setWidth("110px").setFlexGrow(0);
        grid.addColumn(r -> formatDuration(r.getDurationMs())).setHeader("耗时")
                .setTextAlign(ColumnTextAlign.END).setWidth("100px").setFlexGrow(0);
        grid.addComponentColumn(this::sessionCell).setHeader("会话").setWidth("130px").setFlexGrow(0);
        grid.addClassName("tm-grid");
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

        panel.add(grid, paginationBar);
        return panel;
    }

    /**
     * 来源徽标：admin 管理端（绿）/ api 开放接口（蓝），未知来源原样展示
     */
    private Component sourceBadge(AgentTokenUsage record) {
        String source = record.getSource();
        if (AgentTokenUsage.SOURCE_ADMIN.equals(source)) {
            Span badge = new Span("管理端");
            badge.addClassNames("tm-badge", "tm-badge-admin");
            return badge;
        }
        if (AgentTokenUsage.SOURCE_API.equals(source)) {
            Span badge = new Span("开放接口");
            badge.addClassNames("tm-badge", "tm-badge-api");
            return badge;
        }
        return new Span(source == null || source.isBlank() ? "-" : source);
    }

    /**
     * 会话 ID：截断为 8 位前缀 + …，完整值放 title 悬停
     */
    private Component sessionCell(AgentTokenUsage record) {
        String sessionId = record.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return new Span("-");
        }
        Span span = new Span(sessionId.length() > 8 ? sessionId.substring(0, 8) + "…" : sessionId);
        span.addClassName("tm-session");
        span.setTitle(sessionId);
        return span;
    }

    private void loadPage(int page, int pageSize) {
        Long agentId = agentFilter.getValue() == null ? null : agentFilter.getValue().getId();
        String source = sourceFilter.getValue();
        Page<AgentTokenUsage> result = tokenUsageService.pageRecords(agentId,
                source == null || source.isBlank() ? null : source, page, pageSize);
        grid.setItems(result.getRecords());
        paginationBar.setTotal(result.getTotal());
    }

    // ---------- 组件小工具 ----------

    /**
     * 面板容器：图标标题 + 头部右侧自定义内容 + 白底圆角卡片
     */
    private Div panel(String title, Component headRight, VaadinIcon vaadinIcon) {
        Icon icon = vaadinIcon.create();
        icon.addClassName("tm-panel-icon");
        H3 heading = new H3(title);
        heading.addClassName("tm-panel-title");
        Div headLeft = new Div(icon, heading);
        headLeft.addClassName("tm-panel-head-left");
        Div head = new Div(headLeft, headRight);
        head.addClassName("tm-panel-head");
        Div panel = new Div(head);
        panel.addClassName("tm-panel");
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

    /**
     * 明细表格数字：null 显示 -，否则千分位全量展示
     */
    private static String intNum(Integer value) {
        return value == null ? "-" : NumberFormat.getInstance().format(value);
    }

    /**
     * 大数字缩写：<1000 原样，<1,000,000 保留一位小数 k（如 12.3k），否则 M（如 1.2M）
     */
    private static String abbrev(Long value) {
        return abbrev(value == null ? 0 : value);
    }

    private static String abbrev(long value) {
        if (value < 1000) {
            return String.valueOf(value);
        }
        if (value < 1_000_000) {
            return String.format("%.1f", value / 1000.0) + "k";
        }
        return String.format("%.1f", value / 1_000_000.0) + "M";
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
