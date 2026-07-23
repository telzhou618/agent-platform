package com.example.agent.ui;

import com.example.agent.system.dto.ToolInfo;
import com.example.agent.system.service.ToolService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/** 工具管理：展示系统中 @Tool 注解解析出的工具列表，只读不落库 */
@Route(value = "tools", layout = MainLayout.class)
@PageTitle("工具管理 - agent-platform")
public class ToolView extends VerticalLayout {

    private final ToolService toolService;
    private final Grid<ToolInfo> grid = new Grid<>(ToolInfo.class, false);

    public ToolView(ToolService toolService) {
        this.toolService = toolService;
        setSizeFull();

        H2 title = new H2("工具管理");
        title.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-xl)");

        Span hint = new Span("系统工具由 @Tool 注解自动解析，扩展新工具只需新增带注解的组件类");
        hint.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        Button reload = new Button("刷新", e -> loadItems());
        reload.addThemeVariants(ButtonVariant.LUMO_SMALL);

        HorizontalLayout toolbar = new HorizontalLayout(title, hint, reload);
        toolbar.setWidthFull();
        toolbar.expand(hint);
        toolbar.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        grid.addColumn(ToolInfo::getName).setHeader("工具名称").setWidth("200px").setFlexGrow(0);
        grid.addColumn(ToolInfo::getDescription).setHeader("描述");
        grid.addComponentColumn(this::typeBadge).setHeader("类型").setWidth("110px").setFlexGrow(0);
        grid.addColumn(ToolInfo::getSourceClass).setHeader("来源类").setWidth("180px").setFlexGrow(0);
        grid.addComponentColumn(this::paramsButton).setHeader("参数").setWidth("120px").setFlexGrow(0);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

        add(toolbar, grid);
        loadItems();
    }

    /** 类型徽标 */
    private Component typeBadge(ToolInfo tool) {
        Span badge = new Span(tool.getType());
        badge.getElement().getThemeList().add("badge success");
        return badge;
    }

    /** 查看参数 JSON Schema */
    private Component paramsButton(ToolInfo tool) {
        Button view = new Button("查看参数", e -> {
            Dialog dialog = new Dialog();
            dialog.setHeaderTitle(tool.getName() + " 参数 JSON Schema");
            Pre json = new Pre(tool.getParamsJson());
            json.getStyle()
                    .set("background", "var(--lumo-contrast-5pct)")
                    .set("padding", "var(--lumo-space-m)")
                    .set("border-radius", "var(--lumo-border-radius-m)")
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("margin", "0")
                    .set("white-space", "pre-wrap");
            dialog.add(json);
            dialog.setWidth("560px");
            dialog.open();
        });
        view.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        return view;
    }

    private void loadItems() {
        grid.setItems(toolService.listTools());
    }
}
