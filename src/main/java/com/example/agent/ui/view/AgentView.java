package com.example.agent.ui.view;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.system.chat.ChatService;
import com.example.agent.system.entity.AgentInfo;
import com.example.agent.system.entity.ModelConfig;
import com.example.agent.system.service.AgentInfoService;
import com.example.agent.system.service.ModelConfigService;
import com.example.agent.ui.MainLayout;
import com.example.agent.ui.chat.ChatPanel;
import com.example.agent.ui.component.AgentAvatar;
import com.example.agent.ui.component.Notify;
import com.example.agent.ui.component.PaginationBar;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 智能体管理列表页。
 * 新增/编辑在智能体配置页（/agent-config）进行：左窄右宽分栏，按分区配置；
 * 本页保留列表、对话弹窗、启用/禁用与删除。
 */
@Route(value = "agents", layout = MainLayout.class)
@PageTitle("智能体管理 - agent-platform")
public class AgentView extends VerticalLayout {

    private final AgentInfoService agentService;
    private final ModelConfigService modelService;
    private final ChatService chatService;
    private final Grid<AgentInfo> grid = new Grid<>(AgentInfo.class, false);
    private final TextField keyword = new TextField();
    private final PaginationBar paginationBar = new PaginationBar(this::loadPage);

    /**
     * 模型 ID -> 模型，供 Grid 展示名称
     */
    private Map<Long, ModelConfig> modelMap = Map.of();

    public AgentView(AgentInfoService agentService, ModelConfigService modelService,
                     ChatService chatService) {
        this.agentService = agentService;
        this.modelService = modelService;
        this.chatService = chatService;
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
        Button add = new Button("新增智能体", new Icon(VaadinIcon.PLUS),
                e -> getUI().ifPresent(ui -> ui.navigate("agent-config/new")));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        HorizontalLayout toolbar = new HorizontalLayout(title, keyword, search, add);
        toolbar.setWidthFull();
        toolbar.expand(title);
        toolbar.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        grid.addColumn(AgentInfo::getId).setHeader("ID").setWidth("80px").setFlexGrow(0);
        grid.addComponentColumn(this::nameCell).setHeader("名称");
        grid.addComponentColumn(this::statusBadge).setHeader("状态").setWidth("90px").setFlexGrow(0);
        grid.addColumn(a -> modelName(a.getModelId())).setHeader("模型");
        grid.addColumn(a -> StrUtil.brief(StrUtil.nullToEmpty(a.getSysPrompt()), 30)).setHeader("系统提示词");
        grid.addColumn(a -> parseTools(a.getTools()).size()).setHeader("系统工具").setWidth("90px").setFlexGrow(0);
        grid.addColumn(a -> idCount(a.getCustomTools())).setHeader("自定义工具").setWidth("90px").setFlexGrow(0);
        grid.addColumn(a -> idCount(a.getMcpServers())).setHeader("MCP服务").setWidth("90px").setFlexGrow(0);
        grid.addColumn(a -> idCount(a.getKnowledgeBases())).setHeader("知识库").setWidth("90px").setFlexGrow(0);
        grid.addColumn(a -> idCount(a.getSkillRepos())).setHeader("技能仓库").setWidth("90px").setFlexGrow(0);
        grid.addColumn(a -> StrUtil.nullToEmpty(a.getDescription())).setHeader("描述");
        grid.addColumn(a -> DateUtil.format(a.getCreateTime(), "yyyy-MM-dd HH:mm:ss")).setHeader("创建时间");
        grid.addComponentColumn(this::actionButtons).setHeader("操作").setWidth("300px").setFlexGrow(0);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

        add(toolbar, grid, paginationBar);
        refresh();
    }

    /** 名称列：头像（emoji/图片/首字符兜底）+ 名称 */
    private Component nameCell(AgentInfo agent) {
        HorizontalLayout cell = new HorizontalLayout(
                AgentAvatar.create(agent.getAvatar(), agent.getName(), 24), new Span(agent.getName()));
        cell.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        cell.getStyle().set("gap", "8px");
        return cell;
    }

    private Component actionButtons(AgentInfo agent) {
        boolean enabled = agent.isEnabled();
        Button chat = new Button("对话", e -> openChatDialog(agent));
        chat.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        chat.setVisible(enabled);
        Button edit = new Button("编辑",
                e -> getUI().ifPresent(ui -> ui.navigate("agent-config/" + agent.getId())));
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        Button toggle = new Button(enabled ? "禁用" : "启用", e -> toggleStatus(agent));
        toggle.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        Button delete = new Button("删除", e -> confirmDelete(agent));
        delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        return new HorizontalLayout(chat, edit, toggle, delete);
    }

    /**
     * 对话弹窗：大尺寸可拖拽弹窗内嵌对话面板（ChatPanel），预选当前智能体，可关闭
     */
    private void openChatDialog(AgentInfo agent) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("与「" + agent.getName() + "」对话");
        dialog.setWidth("min(1000px, 92vw)");
        dialog.setHeight("82vh");
        dialog.setModal(true);
        dialog.setDraggable(true);
        dialog.setResizable(true);
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(false);

        Button close = new Button(new Icon(VaadinIcon.CLOSE), e -> dialog.close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getHeader().add(close);

        dialog.add(new ChatPanel(agentService.listEnabled(), chatService, agent));
        dialog.open();
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
     * JSON ID 数组字符串 -> 数量（列表页只展示数量，不解析名称）
     */
    private int idCount(String json) {
        if (StrUtil.isBlank(json)) {
            return 0;
        }
        return JSONUtil.toList(json, Long.class).size();
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
