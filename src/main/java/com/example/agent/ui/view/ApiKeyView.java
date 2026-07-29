package com.example.agent.ui.view;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.system.entity.ApiKey;
import com.example.agent.system.service.ApiKeyService;
import com.example.agent.ui.MainLayout;
import com.example.agent.ui.component.Notify;
import com.example.agent.ui.component.PaginationBar;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/** ApiKey 管理：普通用户只能看/操作自己的 Key，管理员看全部（由租户拦截器保证） */
@Route(value = "apikey", layout = MainLayout.class)
@PageTitle("ApiKey管理 - agent-platform")
public class ApiKeyView extends VerticalLayout {

    private final ApiKeyService apiKeyService;
    private final Grid<ApiKey> grid = new Grid<>(ApiKey.class, false);
    private final TextField keyword = new TextField();
    private final PaginationBar paginationBar = new PaginationBar(this::loadPage);

    public ApiKeyView(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
        setSizeFull();

        H2 title = new H2("ApiKey 管理");
        title.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-xl)");

        keyword.setPlaceholder("名称 / 备注");
        keyword.setClearButtonVisible(true);
        keyword.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        keyword.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        keyword.addKeyPressListener(Key.ENTER, e -> paginationBar.reset());
        Button search = new Button("搜索", e -> paginationBar.reset());
        search.addThemeVariants(ButtonVariant.LUMO_SMALL);
        Button add = new Button("新增 ApiKey", new Icon(VaadinIcon.PLUS), e -> openDialog(new ApiKey()));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        HorizontalLayout toolbar = new HorizontalLayout(title, keyword, search, add);
        toolbar.setWidthFull();
        toolbar.expand(title);
        toolbar.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        grid.addColumn(ApiKey::getId).setHeader("ID").setWidth("80px").setFlexGrow(0);
        grid.addColumn(ApiKey::getName).setHeader("名称");
        grid.addComponentColumn(this::keyCell).setHeader("ApiKey").setWidth("260px").setFlexGrow(0);
        grid.addComponentColumn(this::statusBadge).setHeader("状态").setWidth("100px").setFlexGrow(0);
        grid.addColumn(k -> StrUtil.nullToEmpty(k.getRemark())).setHeader("备注");
        grid.addColumn(k -> DateUtil.format(k.getCreateTime(), "yyyy-MM-dd HH:mm:ss")).setHeader("创建时间");
        grid.addComponentColumn(this::actionButtons).setHeader("操作").setWidth("180px").setFlexGrow(0);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

        add(toolbar, grid, paginationBar);
        refresh();
    }

    /** Key 单元格：掩码显示 + 复制按钮 */
    private Component keyCell(ApiKey key) {
        Span masked = new Span(mask(key.getApiKey()));
        Button copy = new Button(new Icon(VaadinIcon.COPY), e -> {
            UI.getCurrent().getPage().executeJs("navigator.clipboard.writeText($0)", key.getApiKey());
            Notify.success("已复制到剪贴板");
        });
        copy.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        copy.setAriaLabel("复制 ApiKey");
        HorizontalLayout cell = new HorizontalLayout(masked, copy);
        cell.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        return cell;
    }

    /** 掩码：保留前 6 位与后 4 位，如 ak-ab12****wxyz */
    private static String mask(String key) {
        if (StrUtil.isBlank(key) || key.length() <= 10) {
            return StrUtil.nullToEmpty(key);
        }
        return key.substring(0, 6) + "****" + key.substring(key.length() - 4);
    }

    private Component statusBadge(ApiKey key) {
        boolean enabled = Integer.valueOf(1).equals(key.getStatus());
        Span badge = new Span(enabled ? "启用" : "禁用");
        badge.getElement().getThemeList().add(enabled ? "badge success" : "badge error");
        return badge;
    }

    private Component actionButtons(ApiKey key) {
        Button edit = new Button("编辑", e -> openDialog(key));
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        Button delete = new Button("删除", e -> confirmDelete(key));
        delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        return new HorizontalLayout(edit, delete);
    }

    private void refresh() {
        paginationBar.refresh();
    }

    private void loadPage(int page, int pageSize) {
        Page<ApiKey> result = apiKeyService.pageApiKeys(keyword.getValue(), page, pageSize);
        grid.setItems(result.getRecords());
        paginationBar.setTotal(result.getTotal());
    }

    /** 新增 / 编辑对话框：Key 值由服务端生成不可改，表单只维护名称 / 状态 / 备注 */
    private void openDialog(ApiKey key) {
        boolean isNew = key.getId() == null;
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(isNew ? "新增 ApiKey" : "编辑 ApiKey");
        dialog.setWidth("480px");

        TextField name = new TextField("名称");
        name.setPlaceholder("如：生产环境调用");
        name.setMaxLength(64);
        Checkbox enabled = new Checkbox("启用");
        TextField remark = new TextField("备注");
        remark.setMaxLength(256);

        Binder<ApiKey> binder = new Binder<>(ApiKey.class);
        binder.forField(name).asRequired("名称不能为空").bind(ApiKey::getName, ApiKey::setName);
        binder.forField(enabled)
                .withConverter(v -> v ? 1 : 0, v -> Integer.valueOf(1).equals(v))
                .bind(ApiKey::getStatus, ApiKey::setStatus);
        binder.bind(remark, ApiKey::getRemark, ApiKey::setRemark);
        name.setRequiredIndicatorVisible(true);

        // 新增默认启用：默认值写在 bean 上，readBean 会同步到字段
        if (isNew) {
            key.setStatus(1);
        }
        binder.readBean(key);

        FormLayout form = new FormLayout(name, enabled, remark);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        dialog.add(form);
        // 编辑时展示 Key（只读 + 脱敏）
        if (!isNew) {
            TextField keyValue = new TextField("ApiKey");
            keyValue.setValue(mask(key.getApiKey()));
            keyValue.setReadOnly(true);
            keyValue.setWidthFull();
            dialog.add(keyValue);
        }

        Button cancel = new Button("取消", e -> dialog.close());
        Button save = new Button("保存", e -> {
            if (!binder.writeBeanIfValid(key)) {
                return;
            }
            try {
                apiKeyService.saveApiKey(key);
                dialog.close();
                refresh();
                Notify.success(isNew ? "创建成功，请在列表复制 ApiKey" : "保存成功");
            } catch (Exception ex) {
                Notify.error(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private void confirmDelete(ApiKey key) {
        ConfirmDialog dialog = new ConfirmDialog("删除 ApiKey",
                "确定删除 ApiKey「" + key.getName() + "」吗？", "删除", e -> {
            try {
                apiKeyService.deleteApiKey(key.getId());
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
