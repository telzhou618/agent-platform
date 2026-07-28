package com.example.agent.ui;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.system.entity.CustomTool;
import com.example.agent.system.service.CustomToolService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义工具：HTTP 远程接口代理工具的 CRUD，用户级数据权限（自己或管理员可见）
 */
@Route(value = "custom-tools", layout = MainLayout.class)
@PageTitle("自定义工具 - agent-platform")
public class CustomToolView extends VerticalLayout {

    /**
     * 请求方式
     */
    private static final List<String> METHODS = List.of("GET", "POST", "PUT", "DELETE");
    /**
     * 请求体类型 -> 展示名
     */
    private static final Map<String, String> REQUEST_TYPES = new LinkedHashMap<>() {{
        put("json", "JSON（application/json）");
        put("form", "表单（x-www-form-urlencoded）");
    }};
    /**
     * 参数类型
     */
    private static final List<String> PARAM_TYPES = List.of("string", "number", "boolean");

    private final CustomToolService customToolService;
    private final Grid<CustomTool> grid = new Grid<>(CustomTool.class, false);
    private final TextField keyword = new TextField();
    private final PaginationBar paginationBar = new PaginationBar(this::loadPage);

    public CustomToolView(CustomToolService customToolService) {
        this.customToolService = customToolService;
        setSizeFull();

        H2 title = new H2("自定义工具");
        title.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-xl)");

        keyword.setPlaceholder("标识 / 名称 / 描述");
        keyword.setClearButtonVisible(true);
        keyword.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        keyword.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        keyword.addKeyPressListener(Key.ENTER, e -> paginationBar.reset());
        Button search = new Button("搜索", e -> paginationBar.reset());
        search.addThemeVariants(ButtonVariant.LUMO_SMALL);
        Button add = new Button("新增自定义工具", new Icon(VaadinIcon.PLUS), e -> openDialog(new CustomTool()));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        HorizontalLayout toolbar = new HorizontalLayout(title, keyword, search, add);
        toolbar.setWidthFull();
        toolbar.expand(title);
        toolbar.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        grid.addColumn(CustomTool::getId).setHeader("ID").setWidth("70px").setFlexGrow(0);
        grid.addColumn(CustomTool::getToolKey).setHeader("工具标识");
        grid.addColumn(CustomTool::getName).setHeader("名称");
        grid.addComponentColumn(t -> methodBadge(t.getMethod())).setHeader("请求方式").setWidth("100px").setFlexGrow(0);
        grid.addColumn(t -> StrUtil.brief(StrUtil.nullToEmpty(t.getUrl()), 40)).setHeader("接口地址");
        grid.addColumn(t -> StrUtil.brief(StrUtil.nullToEmpty(t.getDescription()), 30)).setHeader("描述");
        grid.addColumn(t -> DateUtil.format(t.getUpdateTime(), "yyyy-MM-dd HH:mm:ss")).setHeader("更新时间");
        grid.addComponentColumn(this::actionButtons).setHeader("操作").setWidth("180px").setFlexGrow(0);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

        add(toolbar, grid, paginationBar);
        refresh();
    }

    private Component actionButtons(CustomTool tool) {
        Button edit = new Button("编辑", e -> openDialog(tool));
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        Button delete = new Button("删除", e -> confirmDelete(tool));
        delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        return new HorizontalLayout(edit, delete);
    }

    /**
     * 请求方式徽标
     */
    private Component methodBadge(String method) {
        Span badge = new Span(StrUtil.nullToEmpty(method));
        badge.getElement().getThemeList().add(
                "GET".equalsIgnoreCase(method) ? "badge success" : "badge contrast");
        return badge;
    }

    private void refresh() {
        paginationBar.refresh();
    }

    private void loadPage(int page, int pageSize) {
        Page<CustomTool> result = customToolService.pageCustomTools(keyword.getValue(), page, pageSize);
        grid.setItems(result.getRecords());
        paginationBar.setTotal(result.getTotal());
    }

    /**
     * 新增 / 编辑对话框：基础字段走 Binder；参数定义用动态行编辑器（不挂 Binder），
     * 保存时校验并组装 JSON 数组存入 params
     */
    private void openDialog(CustomTool tool) {
        boolean isNew = tool.getId() == null;
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(isNew ? "新增自定义工具" : "编辑自定义工具");
        dialog.setWidth("820px");

        TextField toolKey = new TextField("工具标识");
        toolKey.setPlaceholder("如：get_public_ip（唯一，模型调用名）");
        toolKey.setMaxLength(64);
        toolKey.setHelperText("小写字母开头的小写字母/数字/下划线");
        TextField name = new TextField("工具名称");
        name.setPlaceholder("如：查询公网 IP");
        name.setMaxLength(64);
        TextArea description = new TextArea("工具描述");
        description.setPlaceholder("说明工具能做什么，模型据此决定何时调用");
        description.setMaxLength(512);
        description.setMinHeight("5em");
        TextField url = new TextField("接口地址");
        url.setPlaceholder("如：https://api.example.com/users/{id}");
        url.setMaxLength(512);
        url.setHelperText("支持 {参数名} 路径占位符");
        Select<String> method = new Select<>();
        method.setLabel("请求方式");
        method.setItems(METHODS);
        Select<String> requestType = new Select<>();
        requestType.setLabel("请求类型");
        requestType.setItems(REQUEST_TYPES.keySet());
        requestType.setItemLabelGenerator(REQUEST_TYPES::get);
        requestType.setHelperText("仅 POST/PUT 时生效");
        // 请求头键值对编辑器（与 MCP 服务一致）
        HeadersEditor headersEditor = new HeadersEditor(tool.getHeaders());

        // 请求类型仅 POST/PUT 时展示
        method.addValueChangeListener(e ->
                requestType.setVisible("POST".equals(e.getValue()) || "PUT".equals(e.getValue())));

        // 参数动态行编辑器
        H4 paramsTitle = new H4("参数定义");
        paramsTitle.getStyle().set("margin", "var(--lumo-space-s) 0 0 0").set("font-size",
                "var(--lumo-font-size-m)");
        VerticalLayout paramsList = new VerticalLayout();
        paramsList.setPadding(false);
        paramsList.setSpacing(false);
        paramsList.getStyle().set("gap", "var(--lumo-space-xs)");
        Button addParam = new Button("添加参数", new Icon(VaadinIcon.PLUS),
                e -> paramsList.add(paramRow(null, paramsList)));
        addParam.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        Binder<CustomTool> binder = new Binder<>(CustomTool.class);
        binder.forField(toolKey).asRequired("工具标识不能为空")
                .withValidator(k -> k.matches(CustomToolService.TOOL_KEY_PATTERN),
                        "应为小写字母开头的小写字母/数字/下划线")
                .bind(CustomTool::getToolKey, CustomTool::setToolKey);
        binder.forField(name).asRequired("工具名称不能为空").bind(CustomTool::getName, CustomTool::setName);
        binder.forField(description).asRequired("工具描述不能为空")
                .bind(CustomTool::getDescription, CustomTool::setDescription);
        binder.forField(url).asRequired("接口地址不能为空")
                .withValidator(FormValidators.url())
                .bind(CustomTool::getUrl, CustomTool::setUrl);
        binder.forField(method).asRequired("请选择请求方式").bind(CustomTool::getMethod, CustomTool::setMethod);

        toolKey.setRequiredIndicatorVisible(true);
        name.setRequiredIndicatorVisible(true);
        description.setRequiredIndicatorVisible(true);
        url.setRequiredIndicatorVisible(true);
        method.setRequiredIndicatorVisible(true);

        // 新增时的默认值写在 bean 上：readBean 会用 bean 值刷新字段
        if (isNew) {
            tool.setMethod("GET");
            tool.setRequestType("json");
        }
        binder.readBean(tool);
        requestType.setValue(StrUtil.blankToDefault(tool.getRequestType(), "json"));
        requestType.setVisible("POST".equals(method.getValue()) || "PUT".equals(method.getValue()));
        // 编辑时回填参数行
        for (JSONObject param : parseParams(tool.getParams())) {
            paramsList.add(paramRow(param, paramsList));
        }

        FormLayout form = new FormLayout(toolKey, name, url, method, requestType, description);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
        form.setColspan(url, 2);
        form.setColspan(description, 2);
        VerticalLayout layout = new VerticalLayout(form, headersEditor, paramsTitle, paramsList, addParam);
        layout.setPadding(false);
        dialog.add(layout);

        Button cancel = new Button("取消", e -> dialog.close());
        Button save = new Button("保存", e -> {
            if (!binder.writeBeanIfValid(tool)) {
                return;
            }
            String params = buildParamsJson(paramsList);
            String headers = headersEditor.toJson();

            tool.setParams(params == null ? "[]" : params);
            tool.setRequestType(requestType.getValue());
            tool.setHeaders(headers == null ? "{}" : headers);
            try {
                customToolService.saveCustomTool(tool);
                dialog.close();
                refresh();
                Notify.success("保存成功");
            } catch (Exception ex) {
                Notify.error(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    /**
     * 一行参数编辑器：名称 + 类型 + 必填 + 描述 + 删除
     */
    private HorizontalLayout paramRow(JSONObject param, VerticalLayout paramsList) {
        TextField paramName = new TextField();
        paramName.setPlaceholder("参数名");
        paramName.setWidth("150px");
        Select<String> type = new Select<>();
        type.setItems(PARAM_TYPES);
        type.setValue("string");
        type.setWidth("110px");
        Checkbox required = new Checkbox("必填");
        TextField paramDesc = new TextField();
        paramDesc.setPlaceholder("参数描述（模型据此填参）");
        paramDesc.setWidthFull();
        Button remove = new Button(new Icon(VaadinIcon.CLOSE_SMALL));
        remove.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        HorizontalLayout row = new HorizontalLayout(paramName, type, required, paramDesc, remove);
        row.setWidthFull();
        row.expand(paramDesc);
        row.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        remove.addClickListener(e -> paramsList.remove(row));
        if (param != null) {
            paramName.setValue(StrUtil.nullToEmpty(param.getStr("name")));
            type.setValue(StrUtil.blankToDefault(param.getStr("type"), "string"));
            required.setValue(param.getBool("required", false));
            paramDesc.setValue(StrUtil.nullToEmpty(param.getStr("description")));
        }
        return row;
    }

    /**
     * 解析参数 JSON 数组为对象列表；非法时返回空
     */
    private List<JSONObject> parseParams(String paramsJson) {
        if (StrUtil.isBlank(paramsJson) || !JSONUtil.isTypeJSON(paramsJson)) {
            return List.of();
        }
        List<JSONObject> result = new ArrayList<>();
        JSONArray array = JSONUtil.parseArray(paramsJson);
        for (int i = 0; i < array.size(); i++) {
            result.add(array.getJSONObject(i));
        }
        return result;
    }

    /**
     * 收集参数行组装 JSON 数组；存在空参数名或重复名时提示并返回 null
     */
    @SuppressWarnings("unchecked")
    private String buildParamsJson(VerticalLayout paramsList) {
        JSONArray params = JSONUtil.createArray();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < paramsList.getComponentCount(); i++) {
            HorizontalLayout row = (HorizontalLayout) paramsList.getComponentAt(i);
            TextField paramName = (TextField) row.getComponentAt(0);
            Select<String> type = (Select<String>) row.getComponentAt(1);
            Checkbox required = (Checkbox) row.getComponentAt(2);
            TextField paramDesc = (TextField) row.getComponentAt(3);
            if (StrUtil.isBlank(paramName.getValue())) {
                Notify.error("参数名不能为空，请补全或删除多余参数行");
                return null;
            }
            if (names.contains(paramName.getValue().trim())) {
                Notify.error("参数名重复：" + paramName.getValue());
                return null;
            }
            names.add(paramName.getValue().trim());
            JSONObject param = JSONUtil.createObj();
            param.set("name", paramName.getValue().trim());
            param.set("type", type.getValue());
            param.set("description", StrUtil.trimToEmpty(paramDesc.getValue()));
            param.set("required", required.getValue());
            params.add(param);
        }
        return params.isEmpty() ? null : params.toString();
    }

    private void confirmDelete(CustomTool tool) {
        ConfirmDialog dialog = new ConfirmDialog("删除自定义工具",
                "确定删除自定义工具「" + tool.getName() + "」吗？引用它的智能体将移除该工具。",
                "删除", e -> {
            try {
                customToolService.deleteCustomTool(tool.getId());
                refresh();
                Notify.success("删除成功");
            } catch (Exception ex) {
                Notify.error(ex.getMessage());
            }
        });
        dialog.setConfirmButtonTheme("error primary");
        dialog.setCancelable(true);
        dialog.setCancelText("取消");
        dialog.open();
    }

    /**
     * 请求头键值对编辑器（与 MCP 服务一致）：首行默认 key 为 Authorization，可增删多行；
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
            initial.forEach(this::addRow);
        }

        /**
         * 汇总为 JSON 对象字符串；无有效行时返回 null
         */
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
            JSONObject obj = JSONUtil.parseObj(headersJson);
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
