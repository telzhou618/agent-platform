package com.example.agent.ui;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.system.entity.AgentInfo;
import com.example.agent.system.entity.CustomTool;
import com.example.agent.system.entity.KnowledgeBase;
import com.example.agent.system.entity.McpServer;
import com.example.agent.system.entity.ModelConfig;
import com.example.agent.system.entity.SkillRepo;
import com.example.agent.system.service.AgentInfoService;
import com.example.agent.system.service.CustomToolService;
import com.example.agent.system.service.KnowledgeBaseService;
import com.example.agent.system.service.McpServerService;
import com.example.agent.system.service.ModelConfigService;
import com.example.agent.system.service.SkillRepoService;
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
import com.vaadin.flow.component.html.Paragraph;
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
    private final KnowledgeBaseService knowledgeBaseService;
    private final SkillRepoService skillRepoService;
    private final CustomToolService customToolService;
    private final Grid<AgentInfo> grid = new Grid<>(AgentInfo.class, false);
    private final TextField keyword = new TextField();
    private final PaginationBar paginationBar = new PaginationBar(this::loadPage);

    /**
     * 知识库类型 -> 展示名
     */
    private static final Map<String, String> KNOWLEDGE_TYPES = Map.of(
            KnowledgeBase.TYPE_BAILIAN, "阿里云百炼",
            KnowledgeBase.TYPE_DIFY, "Dify");

    /**
     * 模型 ID -> 模型，供 Grid 展示名称
     */
    private Map<Long, ModelConfig> modelMap = Map.of();

    /**
     * 各类关联资源 ID -> 名称，供 Grid 徽标列展示
     */
    private Map<Long, String> customToolNames = Map.of();
    private Map<Long, String> mcpNames = Map.of();
    private Map<Long, String> kbNames = Map.of();
    private Map<Long, String> repoNames = Map.of();

    public AgentView(AgentInfoService agentService, ModelConfigService modelService,
                     ToolService toolService, McpServerService mcpServerService,
                     KnowledgeBaseService knowledgeBaseService, SkillRepoService skillRepoService,
                     CustomToolService customToolService) {
        this.agentService = agentService;
        this.modelService = modelService;
        this.toolService = toolService;
        this.mcpServerService = mcpServerService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.skillRepoService = skillRepoService;
        this.customToolService = customToolService;
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
        grid.addComponentColumn(this::statusBadge).setHeader("状态").setWidth("90px").setFlexGrow(0);
        grid.addColumn(a -> modelName(a.getModelId())).setHeader("模型");
        grid.addColumn(a -> StrUtil.brief(StrUtil.nullToEmpty(a.getSysPrompt()), 30)).setHeader("系统提示词");
        grid.addComponentColumn(a -> badges(parseTools(a.getTools()))).setHeader("系统工具");
        grid.addComponentColumn(a -> badges(idNames(a.getCustomTools(), customToolNames))).setHeader("自定义工具");
        grid.addComponentColumn(a -> badges(idNames(a.getMcpServers(), mcpNames))).setHeader("MCP服务");
        grid.addComponentColumn(a -> badges(idNames(a.getKnowledgeBases(), kbNames))).setHeader("知识库");
        grid.addComponentColumn(a -> badges(idNames(a.getSkillRepos(), repoNames))).setHeader("技能仓库");
        grid.addColumn(a -> StrUtil.nullToEmpty(a.getDescription())).setHeader("描述");
        grid.addColumn(a -> DateUtil.format(a.getCreateTime(), "yyyy-MM-dd HH:mm:ss")).setHeader("创建时间");
        grid.addComponentColumn(this::actionButtons).setHeader("操作").setWidth("300px").setFlexGrow(0);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

        add(toolbar, grid, paginationBar);
        refresh();
    }

    private Component actionButtons(AgentInfo agent) {
        boolean enabled = agent.isEnabled();
        Button chat = new Button("对话", e -> getUI().ifPresent(ui -> ui.navigate(ChatView.class, agent.getId())));
        chat.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        chat.setVisible(enabled);
        Button edit = new Button("编辑", e -> openDialog(agent));
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        Button toggle = new Button(enabled ? "禁用" : "启用", e -> toggleStatus(agent));
        toggle.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        Button delete = new Button("删除", e -> confirmDelete(agent));
        delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        return new HorizontalLayout(chat, edit, toggle, delete);
    }

    /** 启用/禁用切换：禁用后销毁容器实例、对话页不可选；启用则重新注册 */
    private void toggleStatus(AgentInfo agent) {
        boolean enable = !agent.isEnabled();
        try {
            agentService.setAgentStatus(agent.getId(), enable);
            refresh();
            Notify.success(enable ? "已启用「" + agent.getName() + "」" : "已禁用「" + agent.getName() + "」");
        } catch (Exception ex) {
            Notify.error(ex.getMessage());
        }
    }

    /** 状态徽标：启用绿色 / 禁用红色 */
    private Component statusBadge(AgentInfo agent) {
        boolean enabled = agent.isEnabled();
        Span badge = new Span(enabled ? "启用" : "禁用");
        badge.getElement().getThemeList().add(enabled ? "badge success" : "badge error");
        return badge;
    }

    /**
     * 名称列表渲染为徽标组
     */
    private Component badges(List<String> names) {
        HorizontalLayout badges = new HorizontalLayout();
        badges.setSpacing(false);
        badges.getStyle().set("gap", "var(--lumo-space-xs)");
        for (String name : names) {
            Span badge = new Span(name);
            badge.getElement().getThemeList().add("badge");
            badges.add(badge);
        }
        return badges;
    }

    /**
     * JSON ID 数组字符串 -> 关联资源名称列表（ID 已删除的名称跳过）
     */
    private List<String> idNames(String json, Map<Long, String> nameMap) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        return JSONUtil.toList(json, Long.class).stream()
                .map(nameMap::get)
                .filter(Objects::nonNull)
                .toList();
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
        customToolNames = customToolService.list().stream()
                .collect(Collectors.toMap(CustomTool::getId, CustomTool::getName));
        mcpNames = mcpServerService.list().stream()
                .collect(Collectors.toMap(McpServer::getId, McpServer::getName));
        kbNames = knowledgeBaseService.list().stream()
                .collect(Collectors.toMap(KnowledgeBase::getId, KnowledgeBase::getName));
        repoNames = skillRepoService.list().stream()
                .collect(Collectors.toMap(SkillRepo::getId, SkillRepo::getName));
        Page<AgentInfo> result = agentService.pageAgents(keyword.getValue(), page, pageSize);
        grid.setItems(result.getRecords());
        paginationBar.setTotal(result.getTotal());
    }

    private void openDialog(AgentInfo agent) {
        boolean isNew = agent.getId() == null;
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(isNew ? "新增智能体" : "编辑智能体");
        dialog.setWidth("840px");

        TextField name = new TextField("名称");
        name.setPlaceholder("如：天气小助手");
        name.setMaxLength(64);

        ComboBox<ModelConfig> model = new ComboBox<>("模型");
        List<ModelConfig> models = modelService.list();
        model.setItems(models);
        model.setItemLabelGenerator(m -> m.getName() + "（" + m.getModel() + "）");

        TextArea sysPrompt = new TextArea("系统提示词");
        sysPrompt.setWidthFull();
        sysPrompt.setMinHeight("8em");
        sysPrompt.setHelperText("保存后会自动追加默认要求：结构化方式输出、回答简洁明了");

        MultiSelectComboBox<String> tools = new MultiSelectComboBox<>("系统工具");
        tools.setItems(toolService.listToolNames());
        tools.setHelperText("可选工具来自「系统工具」中解析出的内置工具，所有人可见；新建时默认全选");

        MultiSelectComboBox<CustomTool> customTools = new MultiSelectComboBox<>("自定义工具");
        List<CustomTool> customToolList = customToolService.list();
        customTools.setItems(customToolList);
        customTools.setItemLabelGenerator(t -> t.getName() + "（" + t.getToolKey() + "）");
        customTools.setHelperText("可选工具来自「自定义工具」（HTTP 远程接口），仅自己或管理员可见");

        MultiSelectComboBox<McpServer> mcpServers = new MultiSelectComboBox<>("MCP服务");
        List<McpServer> mcpList = mcpServerService.list();
        mcpServers.setItems(mcpList);
        mcpServers.setItemLabelGenerator(McpServer::getName);
        mcpServers.setHelperText("可选服务来自「MCP服务管理」，保存后自动挂载其全部工具");

        MultiSelectComboBox<KnowledgeBase> knowledgeBases = new MultiSelectComboBox<>("知识库");
        List<KnowledgeBase> kbList = knowledgeBaseService.list();
        knowledgeBases.setItems(kbList);
        knowledgeBases.setItemLabelGenerator(k -> k.getName() + "（"
                + KNOWLEDGE_TYPES.getOrDefault(k.getType(), StrUtil.nullToEmpty(k.getType())) + "）");
        knowledgeBases.setHelperText("可选知识库来自「知识库管理」，保存后自动挂载检索能力");

        MultiSelectComboBox<SkillRepo> skillRepos = new MultiSelectComboBox<>("技能仓库");
        List<SkillRepo> repoList = skillRepoService.list();
        skillRepos.setItems(repoList);
        skillRepos.setItemLabelGenerator(SkillRepo::getName);
        skillRepos.setHelperText("可选技能仓库来自「技能仓库管理」，保存后自动接入仓库中的技能");

        TextField description = new TextField("描述");
        description.setMaxLength(256);

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
        // 自定义工具多选 <-> JSON ID 数组字符串
        Map<Long, CustomTool> customToolById = customToolList.stream()
                .collect(Collectors.toMap(CustomTool::getId, Function.identity()));
        binder.forField(customTools)
                .withConverter(
                        selected -> CollUtil.isEmpty(selected) ? null
                                : JSONUtil.toJsonStr(selected.stream()
                                .map(CustomTool::getId).sorted().toList()),
                        json -> StrUtil.isBlank(json) ? Set.of()
                                : JSONUtil.toList(json, Long.class).stream()
                                .map(customToolById::get)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toCollection(LinkedHashSet::new)))
                .bind(AgentInfo::getCustomTools, AgentInfo::setCustomTools);
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
        // 知识库多选 <-> JSON ID 数组字符串
        Map<Long, KnowledgeBase> kbById = kbList.stream()
                .collect(Collectors.toMap(KnowledgeBase::getId, Function.identity()));
        binder.forField(knowledgeBases)
                .withConverter(
                        selected -> CollUtil.isEmpty(selected) ? null
                                : JSONUtil.toJsonStr(selected.stream()
                                .map(KnowledgeBase::getId).sorted().toList()),
                        json -> StrUtil.isBlank(json) ? Set.of()
                                : JSONUtil.toList(json, Long.class).stream()
                                .map(kbById::get)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toCollection(LinkedHashSet::new)))
                .bind(AgentInfo::getKnowledgeBases, AgentInfo::setKnowledgeBases);
        // 技能仓库多选 <-> JSON ID 数组字符串
        Map<Long, SkillRepo> repoById = repoList.stream()
                .collect(Collectors.toMap(SkillRepo::getId, Function.identity()));
        binder.forField(skillRepos)
                .withConverter(
                        selected -> CollUtil.isEmpty(selected) ? null
                                : JSONUtil.toJsonStr(selected.stream()
                                .map(SkillRepo::getId).sorted().toList()),
                        json -> StrUtil.isBlank(json) ? Set.of()
                                : JSONUtil.toList(json, Long.class).stream()
                                .map(repoById::get)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toCollection(LinkedHashSet::new)))
                .bind(AgentInfo::getSkillRepos, AgentInfo::setSkillRepos);
        binder.bind(description, AgentInfo::getDescription, AgentInfo::setDescription);

        name.setRequiredIndicatorVisible(true);
        model.setRequiredIndicatorVisible(true);

        binder.readBean(agent);
        // 新建时默认选中全部系统工具（编辑时保持原配置）
        if (isNew) {
            tools.setValue(new LinkedHashSet<>(toolService.listToolNames()));
        }

        FormLayout form = new FormLayout(name, model, description, sysPrompt, tools, customTools, mcpServers,
                knowledgeBases, skillRepos);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
        form.setColspan(sysPrompt, 2);
        form.setColspan(tools, 2);
        form.setColspan(customTools, 2);
        form.setColspan(mcpServers, 2);
        form.setColspan(knowledgeBases, 2);
        form.setColspan(skillRepos, 2);
        dialog.add(form);

        Button cancel = new Button("取消", e -> dialog.close());
        Button save = new Button("保存", e -> {
            if (!binder.writeBeanIfValid(agent)) {
                return;
            }
            // 保存是重量级操作（注册/重建实例），先展示配置摘要让用户确认
            confirmSave(agent, dialog, isNew, model.getValue(),
                    tools.getValue(), customTools.getValue(), mcpServers.getValue(),
                    knowledgeBases.getValue(), skillRepos.getValue());
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    /**
     * 保存前确认弹窗：展示配置摘要，确认信息完整无误后才执行保存
     */
    private void confirmSave(AgentInfo agent, Dialog editDialog, boolean isNew, ModelConfig model,
                             Set<String> tools, Set<CustomTool> customTools, Set<McpServer> mcpServers,
                             Set<KnowledgeBase> knowledgeBases, Set<SkillRepo> skillRepos) {
        Dialog confirm = new Dialog();
        confirm.setHeaderTitle(isNew ? "确认创建智能体" : "确认保存修改");
        confirm.setWidth("480px");

        VerticalLayout summary = new VerticalLayout();
        summary.setPadding(false);
        summary.setSpacing(false);
        summary.getStyle().set("gap", "var(--lumo-space-xs)");
        addSummaryLine(summary, "名称", agent.getName());
        addSummaryLine(summary, "模型", model == null ? null : model.getName() + "（" + model.getModel() + "）");
        addSummaryLine(summary, "状态", agent.isEnabled() ? "启用" : "禁用");
        addSummaryLine(summary, "系统工具", summarize(tools, Function.identity()));
        addSummaryLine(summary, "自定义工具", summarize(customTools, CustomTool::getName));
        addSummaryLine(summary, "MCP服务", summarize(mcpServers, McpServer::getName));
        addSummaryLine(summary, "知识库", summarize(knowledgeBases, KnowledgeBase::getName));
        addSummaryLine(summary, "技能仓库", summarize(skillRepos, SkillRepo::getName));

        Paragraph hint = new Paragraph("保存后将立即注册/重建智能体实例，请确认以上信息完整无误。");
        hint.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
        confirm.add(summary, hint);

        Button back = new Button("返回修改", e -> confirm.close());
        Button ok = new Button("确认保存", e -> {
            try {
                agentService.saveAgent(agent);
                confirm.close();
                editDialog.close();
                refresh();
                Notify.success("保存成功");
            } catch (Exception ex) {
                Notify.error(ex.getMessage());
            }
        });
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirm.getFooter().add(back, ok);
        confirm.open();
    }

    /** 摘要区一行：加粗标签 + 值 */
    private void addSummaryLine(VerticalLayout layout, String label, String value) {
        Paragraph line = new Paragraph();
        line.getStyle().set("margin", "0");
        Span labelSpan = new Span(label + "：");
        labelSpan.getStyle().set("font-weight", "600");
        line.add(labelSpan, new Span(StrUtil.nullToDefault(value, "-")));
        layout.add(line);
    }

    /** 多选集合 -> 「名称1、名称2（共 n 个）」，空集合显示「无」 */
    private <T> String summarize(Set<T> items, Function<T, String> nameFn) {
        if (CollUtil.isEmpty(items)) {
            return "无";
        }
        List<String> names = items.stream().map(nameFn).toList();
        return String.join("、", names) + "（共 " + items.size() + " 个）";
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
