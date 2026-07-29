package com.example.agent.ui.view;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.system.auth.LoginHelper;
import com.example.agent.system.entity.OperationLog;
import com.example.agent.system.service.OperationLogService;
import com.example.agent.ui.MainLayout;
import com.example.agent.ui.component.PaginationBar;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/** 操作日志（仅管理员可见，只读） */
@Route(value = "logs", layout = MainLayout.class)
@PageTitle("操作日志 - agent-platform")
public class OperationLogView extends VerticalLayout implements BeforeEnterObserver {

    private final OperationLogService operationLogService;
    private final Grid<OperationLog> grid = new Grid<>(OperationLog.class, false);
    private final TextField keyword = new TextField();
    private final PaginationBar paginationBar = new PaginationBar(this::loadPage);

    public OperationLogView(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
        setSizeFull();

        H2 title = new H2("操作日志");
        title.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-xl)");

        keyword.setPlaceholder("操作人 / 模块 / 摘要");
        keyword.setClearButtonVisible(true);
        keyword.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        keyword.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        keyword.addKeyPressListener(Key.ENTER, e -> paginationBar.reset());
        Button search = new Button("搜索", e -> paginationBar.reset());
        search.addThemeVariants(ButtonVariant.LUMO_SMALL);
        HorizontalLayout toolbar = new HorizontalLayout(title, keyword, search);
        toolbar.setWidthFull();
        toolbar.expand(title);
        toolbar.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        grid.addColumn(OperationLog::getId).setHeader("ID").setWidth("80px").setFlexGrow(0);
        grid.addColumn(l -> StrUtil.nullToDefault(l.getUsername(), "-")).setHeader("操作人").setWidth("120px").setFlexGrow(0);
        grid.addColumn(OperationLog::getModule).setHeader("模块").setWidth("140px").setFlexGrow(0);
        grid.addColumn(OperationLog::getAction).setHeader("操作").setWidth("90px").setFlexGrow(0);
        grid.addColumn(l -> StrUtil.nullToEmpty(l.getSummary())).setHeader("摘要");
        grid.addComponentColumn(this::resultBadge).setHeader("结果").setWidth("100px").setFlexGrow(0);
        grid.addColumn(l -> DateUtil.format(l.getCreateTime(), "yyyy-MM-dd HH:mm:ss")).setHeader("操作时间")
                .setWidth("190px").setFlexGrow(0);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

        add(toolbar, grid, paginationBar);
        refresh();
    }

    /** 双保险：非管理员直接访问 /logs 时弹回首页（菜单本身已对非管理员隐藏） */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!LoginHelper.isAdmin()) {
            event.rerouteTo("");
        }
    }

    /** 结果徽标：成功绿 / 失败红，失败悬停显示原因 */
    private Component resultBadge(OperationLog log) {
        boolean success = Integer.valueOf(1).equals(log.getSuccess());
        Span badge = new Span(success ? "成功" : "失败");
        badge.getElement().getThemeList().add(success ? "badge success" : "badge error");
        if (!success && StrUtil.isNotBlank(log.getErrorMsg())) {
            badge.getElement().setAttribute("title", log.getErrorMsg());
        }
        return badge;
    }

    private void refresh() {
        paginationBar.refresh();
    }

    private void loadPage(int page, int pageSize) {
        Page<OperationLog> result = operationLogService.pageLogs(keyword.getValue(), page, pageSize);
        grid.setItems(result.getRecords());
        paginationBar.setTotal(result.getTotal());
    }
}
