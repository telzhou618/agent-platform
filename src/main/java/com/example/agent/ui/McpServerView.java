package com.example.agent.ui;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.system.entity.McpServer;
import com.example.agent.system.service.McpServerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Route(value = "mcp", layout = MainLayout.class)
@PageTitle("MCP服务管理 - agent-platform")
public class McpServerView extends VerticalLayout {

    /** 传输类型 -> 展示名 */
    private static final Map<String, String> TYPES = Map.of(
            McpServer.TYPE_STREAMABLE_HTTP, "可流式传输的 HTTP（streamableHttp）",
            McpServer.TYPE_SSE, "SSE（sse）");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final McpServerService mcpServerService;
    private final Grid<McpServer> grid = new Grid<>(McpServer.class, false);
    private final TextField keyword = new TextField();
    private final PaginationBar paginationBar = new PaginationBar(this::loadPage);

    public McpServerView(McpServerService mcpServerService) {
        this.mcpServerService = mcpServerService;
        setSizeFull();

        H2 title = new H2("MCP服务管理");
        title.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-xl)");

        keyword.setPlaceholder("名称 / 描述");
        keyword.setClearButtonVisible(true);
        keyword.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        keyword.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        keyword.addKeyPressListener(Key.ENTER, e -> paginationBar.reset());
        Button search = new Button("搜索", e -> paginationBar.reset());
        search.addThemeVariants(ButtonVariant.LUMO_SMALL);
        Button add = new Button("新增MCP服务", new Icon(VaadinIcon.PLUS), e -> openDialog(new McpServer()));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        HorizontalLayout toolbar = new HorizontalLayout(title, keyword, search, add);
        toolbar.setWidthFull();
        toolbar.expand(title);
        toolbar.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        grid.addColumn(McpServer::getId).setHeader("ID").setWidth("80px").setFlexGrow(0);
        grid.addColumn(McpServer::getName).setHeader("名称");
        grid.addColumn(s -> TYPES.getOrDefault(s.getType(), StrUtil.nullToEmpty(s.getType())))
                .setHeader("类型");
        grid.addColumn(McpServer::getUrl).setHeader("URL");
        grid.addColumn(s -> StrUtil.nullToEmpty(s.getDescription())).setHeader("描述");
        grid.addComponentColumn(this::statusBadge).setHeader("状态").setWidth("110px").setFlexGrow(0);
        grid.addColumn(s -> DateUtil.format(s.getCreateTime(), "yyyy-MM-dd HH:mm:ss")).setHeader("创建时间");
        grid.addComponentColumn(this::actionButtons).setHeader("操作").setWidth("260px").setFlexGrow(0);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

        add(toolbar, grid, paginationBar);
        refresh();
    }

    private Component actionButtons(McpServer server) {
        Button tools = new Button("工具", e -> openToolsDialog(server));
        tools.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        Button edit = new Button("编辑", e -> openDialog(server));
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        Button delete = new Button("删除", e -> confirmDelete(server));
        delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        return new HorizontalLayout(tools, edit, delete);
    }

    /** 状态徽标：渲染后异步检测，可用绿色、不可用红色 */
    private Component statusBadge(McpServer server) {
        Span badge = new Span("检测中…");
        badge.getElement().setAttribute("theme", "badge contrast");
        UI ui = UI.getCurrent();
        CompletableFuture
                .supplyAsync(() -> mcpServerService.testConnection(server))
                .thenAccept(error -> ui.access(() -> {
                    boolean ok = error == null;
                    badge.setText(ok ? "可用" : "不可用");
                    badge.getElement().setAttribute("theme", ok ? "badge success" : "badge error");
                    if (!ok) {
                        badge.getElement().setAttribute("title", error);
                    }
                }));
        return badge;
    }

    private void refresh() {
        paginationBar.refresh();
    }

    private void loadPage(int page, int pageSize) {
        Page<McpServer> result = mcpServerService.pageMcpServers(keyword.getValue(), page, pageSize);
        grid.setItems(result.getRecords());
        paginationBar.setTotal(result.getTotal());
    }

    /** 新增 / 编辑对话框：保存前服务端会验证可连接，连不上则报错放弃 */
    private void openDialog(McpServer server) {
        boolean isNew = server.getId() == null;
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(isNew ? "新增MCP服务" : "编辑MCP服务");
        dialog.setWidth("640px");

        TextField name = new TextField("名称");
        TextField description = new TextField("描述");
        Select<String> type = new Select<>();
        type.setLabel("类型");
        type.setItems(TYPES.keySet());
        type.setItemLabelGenerator(TYPES::get);
        TextField url = new TextField("URL");
        url.setWidthFull();
        IntegerField timeout = new IntegerField("超时（毫秒）");
        timeout.setMin(1000);
        timeout.setStepButtonsVisible(true);
        timeout.setStep(1000);

        HeadersEditor headersEditor = new HeadersEditor(server.getHeaders());

        Binder<McpServer> binder = new Binder<>(McpServer.class);
        binder.forField(name).asRequired("名称不能为空").bind(McpServer::getName, McpServer::setName);
        binder.bind(description, McpServer::getDescription, McpServer::setDescription);
        binder.forField(type).asRequired("请选择类型").bind(McpServer::getType, McpServer::setType);
        binder.forField(url).asRequired("URL 不能为空").bind(McpServer::getUrl, McpServer::setUrl);
        binder.forField(timeout).asRequired("超时不能为空").bind(McpServer::getTimeout, McpServer::setTimeout);

        name.setRequiredIndicatorVisible(true);
        type.setRequiredIndicatorVisible(true);
        url.setRequiredIndicatorVisible(true);
        if (isNew) {
            type.setValue(McpServer.TYPE_STREAMABLE_HTTP);
            timeout.setValue(30000);
        }
        binder.readBean(server);

        FormLayout form = new FormLayout(name, type, description, timeout);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
        VerticalLayout layout = new VerticalLayout(form, url, headersEditor);
        layout.setPadding(false);
        dialog.add(layout);

        Button cancel = new Button("取消", e -> dialog.close());
        Button save = new Button("保存", e -> {
            if (!binder.writeBeanIfValid(server)) {
                return;
            }
            server.setHeaders(headersEditor.toJson());
            try {
                mcpServerService.saveMcpServer(server);
                dialog.close();
                refresh();
                Notification.show("保存成功");
            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    /** 工具详情：实时从 MCP 服务拉取工具列表 */
    private void openToolsDialog(McpServer server) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("可用工具 - " + server.getName());
        dialog.setWidth("720px");
        dialog.setHeight("80vh");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        Span loading = new Span("正在从 MCP 服务拉取工具列表…");
        loading.getStyle().set("color", "var(--lumo-secondary-text-color)");
        content.add(loading);
        dialog.add(content);
        dialog.open();

        UI ui = UI.getCurrent();
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return mcpServerService.listTools(server);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .thenAccept(tools -> ui.access(() -> {
                    content.removeAll();
                    if (tools == null) {
                        content.add(errorLine("拉取失败：MCP 服务不可用"));
                        return;
                    }
                    if (tools.isEmpty()) {
                        content.add(errorLine("该 MCP 服务没有提供工具"));
                        return;
                    }
                    for (McpSchema.Tool tool : tools) {
                        content.add(toolDetail(tool));
                    }
                }));
    }

    /** 单个工具：标题为工具名，展开查看描述和参数 JSON Schema */
    private Component toolDetail(McpSchema.Tool tool) {
        VerticalLayout body = new VerticalLayout();
        body.setPadding(false);
        body.setSpacing(false);
        body.getStyle().set("gap", "var(--lumo-space-xs)");
        Span desc = new Span(StrUtil.blankToDefault(tool.description(), "（无描述）"));
        desc.getStyle().set("white-space", "pre-wrap")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        body.add(desc);
        try {
            String params = OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(tool.inputSchema());
            Div pre = new Div();
            pre.setText(params);
            pre.addClassName("tool-panel-pre");
            body.add(pre);
        } catch (Exception ignored) {
            // 参数 schema 序列化失败时只展示描述
        }
        Details details = new Details(tool.name(), body);
        details.setWidthFull();
        return details;
    }

    private Component errorLine(String text) {
        Span line = new Span(text);
        line.getStyle().set("color", "var(--lumo-error-text-color)");
        return line;
    }

    private void confirmDelete(McpServer server) {
        ConfirmDialog dialog = new ConfirmDialog("删除MCP服务",
                "确定删除 MCP 服务「" + server.getName() + "」吗？引用它的智能体将移除对应工具。",
                "删除", e -> {
            try {
                mcpServerService.deleteMcpServer(server.getId());
                refresh();
                Notification.show("删除成功");
            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 3000, Notification.Position.MIDDLE);
            }
        });
        dialog.setConfirmButtonTheme("error primary");
        dialog.setCancelable(true);
        dialog.setCancelText("取消");
        dialog.open();
    }

    /**
     * 请求头键值对编辑器：首行默认 key 为 Authorization，可增删多行；
     * toJson() 汇总为 JSON 对象字符串（跳过空 key 行）。
     */
    private static class HeadersEditor extends VerticalLayout {

        private final VerticalLayout rows = new VerticalLayout();

        HeadersEditor(String headersJson) {
            setPadding(false);
            setSpacing(false);
            getStyle().set("gap", "var(--lumo-space-xs)");
            Span label = new Span("请求头");
            label.getStyle().set("font-size", "var(--lumo-font-size-s)")
                    .set("color", "var(--lumo-secondary-text-color)");
            rows.setPadding(false);
            rows.setSpacing(false);
            rows.getStyle().set("gap", "var(--lumo-space-xs)");
            Button addRow = new Button("添加请求头", new Icon(VaadinIcon.PLUS),
                    e -> addRow("", ""));
            addRow.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            add(label, rows, addRow);

            Map<String, String> initial = parse(headersJson);
            if (initial.isEmpty()) {
                addRow("Authorization", "");
            } else {
                initial.forEach(this::addRow);
            }
        }

        /** 汇总为 JSON 对象字符串；无有效行时返回 null */
        String toJson() {
            Map<String, String> map = new LinkedHashMap<>();
            for (Component row : rows.getChildren().toList()) {
                HeaderRow headerRow = (HeaderRow) row;
                if (StrUtil.isNotBlank(headerRow.key.getValue())) {
                    map.put(headerRow.key.getValue().trim(), headerRow.value.getValue());
                }
            }
            return map.isEmpty() ? null : JSONUtil.toJsonStr(map);
        }

        private void addRow(String key, String value) {
            HeaderRow[] holder = new HeaderRow[1];
            HeaderRow row = new HeaderRow(key, value, () -> rows.remove(holder[0]));
            holder[0] = row;
            rows.add(row);
        }

        private static Map<String, String> parse(String headersJson) {
            if (StrUtil.isBlank(headersJson)) {
                return Map.of();
            }
            Map<String, String> map = new LinkedHashMap<>();
            cn.hutool.json.JSONObject obj = JSONUtil.parseObj(headersJson);
            for (String key : obj.keySet()) {
                map.put(key, obj.getStr(key));
            }
            return map;
        }

        private static class HeaderRow extends HorizontalLayout {
            private final TextField key;
            private final TextField value;

            HeaderRow(String k, String v, Runnable onRemove) {
                key = new TextField();
                key.setPlaceholder("Key");
                key.setValue(k);
                key.setWidth("220px");
                value = new TextField();
                value.setPlaceholder("Value");
                value.setValue(StrUtil.nullToEmpty(v));
                value.setWidthFull();
                Button remove = new Button(new Icon(VaadinIcon.CLOSE_SMALL), e -> onRemove.run());
                remove.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY,
                        ButtonVariant.LUMO_ERROR);
                setWidthFull();
                setPadding(false);
                setDefaultVerticalComponentAlignment(Alignment.CENTER);
                expand(value);
                add(key, value, remove);
            }
        }
    }
}
