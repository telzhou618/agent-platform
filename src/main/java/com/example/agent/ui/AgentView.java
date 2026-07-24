package com.example.agent.ui;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.system.entity.AgentInfo;
import com.example.agent.system.entity.McpServer;
import com.example.agent.system.entity.ModelConfig;
import com.example.agent.system.service.AgentInfoService;
import com.example.agent.system.service.McpServerService;
import com.example.agent.system.service.ModelConfigService;
import com.example.agent.system.service.ToolService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Route(value = "agents", layout = MainLayout.class)
@PageTitle("智能体管理 - agent-platform")
public class AgentView extends VerticalLayout {

    private final AgentInfoService agentService;
    private final ModelConfigService modelService;
    private final ToolService toolService;
    private final McpServerService mcpServerService;
    private final Grid<AgentInfo> grid = new Grid<>(AgentInfo.class, false);
    private final TextField keyword = new TextField();
    private final PaginationBar paginationBar = new PaginationBar(this::loadPage);

    /**
     * 模型 ID -> 模型，供 Grid 展示名称
     */
    private Map<Long, ModelConfig> modelMap = Map.of();

    public AgentView(AgentInfoService agentService, ModelConfigService modelService,
                     ToolService toolService, McpServerService mcpServerService) {
        this.agentService = agentService;
        this.modelService = modelService;
        this.toolService = toolService;
        this.mcpServerService = mcpServerService;
        setSizeFull();

        H2 title = new H2("智能体管理");
        title.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-xl)");

        keyword.setPlaceholder("名称 / 描述");
        keyword.setClearButtonVisible(true);
        keyword.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        keyword.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        keyword.addKeyPressListener(Key.ENTER, e -> paginationBar.reset());
        Button search = new Button("搜索", e -> paginationBar.reset());
        search.addThemeVariants(ButtonVariant.LUMO_SMALL);
        Button add = new Button("新增智能体", new Icon(VaadinIcon.PLUS), e -> openDialog(new AgentInfo()));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        HorizontalLayout toolbar = new HorizontalLayout(title, keyword, search, add);
        toolbar.setWidthFull();
        toolbar.expand(title);
        toolbar.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        grid.addColumn(AgentInfo::getId).setHeader("ID").setWidth("80px").setFlexGrow(0);
        grid.addColumn(AgentInfo::getName).setHeader("名称");
        grid.addColumn(a -> modelName(a.getModelId())).setHeader("模型");
        grid.addColumn(a -> StrUtil.brief(StrUtil.nullToEmpty(a.getSysPrompt()), 30)).setHeader("系统提示词");
        grid.addComponentColumn(a -> toolBadges(a.getTools())).setHeader("工具");
        grid.addColumn(a -> StrUtil.nullToEmpty(a.getDescription())).setHeader("描述");
        grid.addColumn(a -> DateUtil.format(a.getCreateTime(), "yyyy-MM-dd HH:mm:ss")).setHeader("创建时间");
        grid.addComponentColumn(this::actionButtons).setHeader("操作").setWidth("240px").setFlexGrow(0);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

        add(toolbar, grid, paginationBar);
        refresh();
    }

    private Component actionButtons(AgentInfo agent) {
        Button chat = new Button("对话", e -> getUI().ifPresent(ui -> ui.navigate(ChatView.class, agent.getId())));
        chat.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        Button edit = new Button("编辑", e -> openDialog(agent));
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        Button delete = new Button("删除", e -> confirmDelete(agent));
        delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        return new HorizontalLayout(chat, edit, delete);
    }

    /**
     * 工具名列表渲染为徽标组
     */
    private Component toolBadges(String toolsJson) {
        HorizontalLayout badges = new HorizontalLayout();
        badges.setSpacing(false);
        badges.getStyle().set("gap", "var(--lumo-space-xs)");
        for (String name : parseTools(toolsJson)) {
            Span badge = new Span(name);
            badge.getElement().getThemeList().add("badge");
            badges.add(badge);
        }
        return badges;
    }

    private String modelName(Long modelId) {
        ModelConfig model = modelMap.get(modelId);
        return model == null ? "" : model.getName() + "(" + model.getModel() + ")";
    }

    /**
     * JSON 数组字符串 -> 工具名列表
     */
    private List<String> parseTools(String toolsJson) {
        if (StrUtil.isBlank(toolsJson)) {
            return List.of();
        }
        return JSONUtil.toList(toolsJson, String.class);
    }

    private void refresh() {
        paginationBar.refresh();
    }

    private void loadPage(int page, int pageSize) {
        modelMap = modelService.list().stream()
                .collect(Collectors.toMap(ModelConfig::getId, Function.identity()));
        Page<AgentInfo> result = agentService.pageAgents(keyword.getValue(), page, pageSize);
        grid.setItems(result.getRecords());
        paginationBar.setTotal(result.getTotal());
    }

    private void openDialog(AgentInfo agent) {
        boolean isNew = agent.getId() == null;
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(isNew ? "新增智能体" : "编辑智能体");

        TextField name = new TextField("名称");

        ComboBox<ModelConfig> model = new ComboBox<>("模型");
        List<ModelConfig> models = modelService.list();
        model.setItems(models);
        model.setItemLabelGenerator(m -> m.getName() + "（" + m.getModel() + "）");

        TextArea sysPrompt = new TextArea("系统提示词");
        sysPrompt.setWidthFull();
        sysPrompt.setMinHeight("8em");
        sysPrompt.setHelperText("保存后会自动追加默认要求：结构化方式输出、回答简洁明了");

        MultiSelectComboBox<String> tools = new MultiSelectComboBox<>("工具");
        tools.setItems(toolService.listToolNames());
        tools.setHelperText("可选工具来自「工具管理」中解析出的系统工具");

        MultiSelectComboBox<McpServer> mcpServers = new MultiSelectComboBox<>("MCP服务");
        List<McpServer> mcpList = mcpServerService.list();
        mcpServers.setItems(mcpList);
        mcpServers.setItemLabelGenerator(McpServer::getName);
        mcpServers.setHelperText("可选服务来自「MCP服务管理」，保存后自动挂载其全部工具");

        TextField description = new TextField("描述");

        // Binder 绑定与校验：校验失败时错误信息红色显示在字段下方
        Binder<AgentInfo> binder = new Binder<>(AgentInfo.class);
        binder.forField(name)
                .asRequired("名称不能为空")
                .bind(AgentInfo::getName, AgentInfo::setName);
        binder.forField(model)
                .asRequired("请选择模型")
                .withConverter(ModelConfig::getId,
                        id -> id == null ? null : modelMap.getOrDefault(id,
                                models.stream().filter(m -> m.getId().equals(id)).findFirst().orElse(null)))
                .bind(AgentInfo::getModelId, AgentInfo::setModelId);
        binder.bind(sysPrompt, AgentInfo::getSysPrompt, AgentInfo::setSysPrompt);
        // 工具多选 <-> JSON 数组字符串
        binder.forField(tools)
                .withConverter(
                        selected -> {
                            if (CollUtil.isEmpty(selected)) {
                                return null;
                            }
                            List<String> names = new ArrayList<>(selected);
                            Collections.sort(names);
                            return JSONUtil.toJsonStr(names);
                        },
                        json -> StrUtil.isBlank(json) ? Set.of() : new LinkedHashSet<>(parseTools(json)))
                .bind(AgentInfo::getTools, AgentInfo::setTools);
        // MCP 服务多选 <-> JSON ID 数组字符串
        Map<Long, McpServer> mcpById = mcpList.stream()
                .collect(Collectors.toMap(McpServer::getId, Function.identity()));
        binder.forField(mcpServers)
                .withConverter(
                        selected -> CollUtil.isEmpty(selected) ? null
                                : JSONUtil.toJsonStr(selected.stream()
                                .map(McpServer::getId).sorted().toList()),
                        json -> StrUtil.isBlank(json) ? Set.of()
                                : JSONUtil.toList(json, Long.class).stream()
                                .map(mcpById::get)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toCollection(LinkedHashSet::new)))
                .bind(AgentInfo::getMcpServers, AgentInfo::setMcpServers);
        binder.bind(description, AgentInfo::getDescription, AgentInfo::setDescription);

        name.setRequiredIndicatorVisible(true);
        model.setRequiredIndicatorVisible(true);

        binder.readBean(agent);

        FormLayout form = new FormLayout(name, model, description, sysPrompt, tools, mcpServers);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
        form.setColspan(sysPrompt, 2);
        form.setColspan(tools, 2);
        form.setColspan(mcpServers, 2);
        dialog.add(form);

        Button cancel = new Button("取消", e -> dialog.close());
        Button save = new Button("保存", e -> {
            if (!binder.writeBeanIfValid(agent)) {
                return;
            }
            try {
                agentService.saveAgent(agent);
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

    private void confirmDelete(AgentInfo agent) {
        ConfirmDialog dialog = new ConfirmDialog("删除智能体",
                "确定删除智能体「" + agent.getName() + "」吗？", "删除", e -> {
            try {
                agentService.deleteAgent(agent.getId());
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
}
