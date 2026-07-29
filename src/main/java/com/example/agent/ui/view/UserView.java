package com.example.agent.ui.view;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.system.auth.LoginHelper;
import com.example.agent.system.entity.SysUser;
import com.example.agent.system.service.SysUserService;
import com.example.agent.ui.MainLayout;
import com.example.agent.ui.component.FormValidators;
import com.example.agent.ui.component.Notify;
import com.example.agent.ui.component.PaginationBar;
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
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/** 用户管理（仅管理员可见、可操作） */
@Route(value = "users", layout = MainLayout.class)
@PageTitle("用户管理 - agent-platform")
public class UserView extends VerticalLayout implements BeforeEnterObserver {

    private final SysUserService sysUserService;
    private final Grid<SysUser> grid = new Grid<>(SysUser.class, false);
    private final TextField keyword = new TextField();
    private final PaginationBar paginationBar = new PaginationBar(this::loadPage);

    public UserView(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
        setSizeFull();

        H2 title = new H2("用户管理");
        title.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-xl)");

        keyword.setPlaceholder("用户名 / 手机号 / 邮箱");
        keyword.setClearButtonVisible(true);
        keyword.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        keyword.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        keyword.addKeyPressListener(Key.ENTER, e -> paginationBar.reset());
        Button search = new Button("搜索", e -> paginationBar.reset());
        search.addThemeVariants(ButtonVariant.LUMO_SMALL);
        Button add = new Button("新增用户", new Icon(VaadinIcon.PLUS), e -> openDialog(new SysUser()));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        HorizontalLayout toolbar = new HorizontalLayout(title, keyword, search, add);
        toolbar.setWidthFull();
        toolbar.expand(title);
        toolbar.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        grid.addColumn(SysUser::getId).setHeader("ID").setWidth("80px").setFlexGrow(0);
        grid.addColumn(SysUser::getUsername).setHeader("用户名");
        grid.addColumn(u -> StrUtil.nullToEmpty(u.getPhone())).setHeader("手机号");
        grid.addColumn(u -> StrUtil.nullToEmpty(u.getEmail())).setHeader("邮箱");
        grid.addComponentColumn(this::roleBadge).setHeader("角色").setWidth("120px").setFlexGrow(0);
        grid.addColumn(u -> DateUtil.format(u.getCreateTime(), "yyyy-MM-dd HH:mm:ss")).setHeader("创建时间");
        grid.addComponentColumn(this::actionButtons).setHeader("操作").setWidth("180px").setFlexGrow(0);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

        add(toolbar, grid, paginationBar);
        refresh();
    }

    /** 双保险：非管理员直接访问 /users 时弹回首页（菜单本身已对非管理员隐藏） */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!LoginHelper.isAdmin()) {
            event.rerouteTo("");
        }
    }

    private Component roleBadge(SysUser user) {
        boolean admin = Integer.valueOf(1).equals(user.getIsAdmin());
        Span badge = new Span(admin ? "管理员" : "普通用户");
        badge.getElement().getThemeList().add(admin ? "badge success" : "badge");
        return badge;
    }

    private Component actionButtons(SysUser user) {
        Button edit = new Button("编辑", e -> openDialog(user));
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        // 管理员账号不可删除，不渲染删除按钮
        if (Integer.valueOf(1).equals(user.getIsAdmin())) {
            return new HorizontalLayout(edit);
        }
        Button delete = new Button("删除", e -> confirmDelete(user));
        delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        return new HorizontalLayout(edit, delete);
    }

    private void refresh() {
        paginationBar.refresh();
    }

    private void loadPage(int page, int pageSize) {
        Page<SysUser> result = sysUserService.pageUsers(keyword.getValue(), page, pageSize);
        grid.setItems(result.getRecords());
        paginationBar.setTotal(result.getTotal());
    }

    private void openDialog(SysUser user) {
        boolean isNew = user.getId() == null;
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(isNew ? "新增用户" : "编辑用户");
        dialog.setWidth("480px");

        TextField username = new TextField("用户名");
        username.setPlaceholder("登录账号");
        username.setMaxLength(64);
        PasswordField password = new PasswordField("密码");
        password.setPlaceholder("至少 6 位");
        password.setHelperText(isNew ? "必填" : "留空表示不修改密码");
        TextField phone = new TextField("手机号");
        phone.setPlaceholder("11 位手机号");
        phone.setMaxLength(32);
        TextField email = new TextField("邮箱");
        email.setPlaceholder("name@example.com");
        email.setMaxLength(128);
        Checkbox admin = new Checkbox("管理员（可看和操作所有数据）");

        Binder<SysUser> binder = new Binder<>(SysUser.class);
        binder.forField(username)
                .asRequired("用户名不能为空")
                .bind(SysUser::getUsername, SysUser::setUsername);
        binder.forField(phone).withValidator(FormValidators.mobile())
                .bind(SysUser::getPhone, SysUser::setPhone);
        binder.forField(email).withValidator(FormValidators.email())
                .bind(SysUser::getEmail, SysUser::setEmail);
        binder.forField(admin)
                .withConverter(v -> v ? 1 : 0, v -> Integer.valueOf(1).equals(v))
                .bind(SysUser::getIsAdmin, SysUser::setIsAdmin);
        username.setRequiredIndicatorVisible(true);
        password.setRequiredIndicatorVisible(isNew);

        binder.readBean(user);

        FormLayout form = new FormLayout(username, password, phone, email, admin);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
        dialog.add(form);

        Button cancel = new Button("取消", e -> dialog.close());
        Button save = new Button("保存", e -> {
            if (!binder.writeBeanIfValid(user)) {
                return;
            }
            if (isNew && StrUtil.isBlank(password.getValue())) {
                Notify.error("新用户必须设置密码");
                return;
            }
            if (StrUtil.isNotBlank(password.getValue()) && password.getValue().length() < 6) {
                Notify.error("密码至少 6 位");
                return;
            }
            // 留空交给 service 处理为“不修改密码”
            user.setPassword(password.getValue());
            try {
                sysUserService.saveUser(user);
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

    private void confirmDelete(SysUser user) {
        ConfirmDialog dialog = new ConfirmDialog("删除用户",
                "确定删除用户「" + user.getUsername() + "」吗？", "删除", e -> {
            try {
                sysUserService.deleteUser(user.getId());
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
