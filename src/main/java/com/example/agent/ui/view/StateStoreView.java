package com.example.agent.ui.view;

import cn.hutool.core.util.StrUtil;
import com.example.agent.system.agent.AgentStateStoreFactory;
import com.example.agent.system.dto.StateStoreInfo;
import com.example.agent.system.service.AgentInfoService;
import com.example.agent.ui.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据存储：列出系统支持的全部 AgentStateStore（内存 / 本地 JSON 文件 / Redis / MySQL），
 * 展示基本配置、实时可用性与使用中的智能体数。可用状态进入页面时自动检测，可手动重新检测。
 */
@Route(value = "state-stores", layout = MainLayout.class)
@PageTitle("数据存储 - agent-platform")
public class StateStoreView extends VerticalLayout {

    private final AgentStateStoreFactory stateStoreFactory;
    private final AgentInfoService agentInfoService;
    private final Grid<StateStoreInfo> grid = new Grid<>(StateStoreInfo.class, false);

    public StateStoreView(AgentStateStoreFactory stateStoreFactory, AgentInfoService agentInfoService) {
        this.stateStoreFactory = stateStoreFactory;
        this.agentInfoService = agentInfoService;
        setSizeFull();

        H2 title = new H2("数据存储");
        title.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-xl)");
        Button recheck = new Button("重新检测", new Icon(VaadinIcon.REFRESH), e -> refresh());
        recheck.addThemeVariants(ButtonVariant.LUMO_SMALL);
        HorizontalLayout toolbar = new HorizontalLayout(title, recheck);
        toolbar.setWidthFull();
        toolbar.expand(title);
        toolbar.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        grid.addComponentColumn(this::typeBadge).setHeader("类型").setWidth("130px").setFlexGrow(0);
        grid.addColumn(StateStoreInfo::name).setHeader("名称").setWidth("150px").setFlexGrow(0);
        grid.addColumn(StateStoreInfo::description).setHeader("说明");
        grid.addColumn(StateStoreInfo::config).setHeader("配置信息");
        grid.addComponentColumn(this::availabilityCell).setHeader("可用状态").setWidth("220px").setFlexGrow(0);
        grid.addColumn(StateStoreInfo::agentCount).setHeader("使用中智能体").setWidth("110px").setFlexGrow(0);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

        add(toolbar, grid);
        refresh();
    }

    /** 进入页面 / 点击重新检测：统计各存储使用中的智能体数并重新做可用性检测 */
    private void refresh() {
        // 存量 state_store 为 null 的记录按默认 jsonfile 统计
        Map<String, Long> usage = agentInfoService.list().stream()
                .collect(Collectors.groupingBy(
                        a -> StrUtil.blankToDefault(a.getStateStore(), AgentStateStoreFactory.TYPE_DEFAULT),
                        Collectors.counting()));
        grid.setItems(stateStoreFactory.listStores(usage));
    }

    /** 类型徽标：四种存储各配一个主题色 */
    private Component typeBadge(StateStoreInfo info) {
        Span badge = new Span(info.key());
        String theme = switch (info.key()) {
            case AgentStateStoreFactory.TYPE_MEMORY -> "badge";
            case AgentStateStoreFactory.TYPE_JSONFILE -> "badge success";
            case AgentStateStoreFactory.TYPE_REDIS -> "badge error";
            case AgentStateStoreFactory.TYPE_MYSQL -> "badge contrast";
            default -> "badge";
        };
        badge.getElement().getThemeList().add(theme);
        return badge;
    }

    /** 可用状态：徽标 + 检测明细小字 */
    private Component availabilityCell(StateStoreInfo info) {
        Span badge = new Span(info.available() ? "可用" : "不可用");
        badge.getElement().getThemeList().add(info.available() ? "badge success" : "badge error");
        Span detail = new Span(info.availableDetail());
        detail.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-secondary-text-color)");
        Div cell = new Div(badge, detail);
        cell.getStyle().set("display", "flex").set("flex-direction", "column");
        return cell;
    }
}
