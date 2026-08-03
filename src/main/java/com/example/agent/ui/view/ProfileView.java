package com.example.agent.ui.view;

import cn.hutool.core.util.StrUtil;
import com.example.agent.system.auth.LoginHelper;
import com.example.agent.system.entity.SysUser;
import com.example.agent.system.service.SysUserService;
import com.example.agent.ui.MainLayout;
import com.example.agent.ui.component.AgentAvatar;
import com.example.agent.ui.component.FormValidators;
import com.example.agent.ui.component.Notify;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import lombok.Data;

import java.util.function.BooleanSupplier;

/**
 * 个人主页（简约卡片式）：顶部居中头像（默认取用户名首字，支持图片 URL 修改），
 * 下方信息行：用户名只读，手机号 / 邮箱脱敏展示、弹窗修改（Binder 表单校验格式），
 * 密码弹窗修改（需验证原密码）。右上角点击账号进入。
 */
@Route(value = "profile", layout = MainLayout.class)
@PageTitle("个人主页 - agent-platform")
public class ProfileView extends VerticalLayout {

    private final SysUserService sysUserService;
    private final SysUser user;

    public ProfileView(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
        this.user = sysUserService.getById(LoginHelper.currentUserId());

        setSizeFull();
        setAlignItems(Alignment.STRETCH);

        Div card = new Div();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "16px")
                .set("padding", "var(--lumo-space-xl)")
                .set("width", "100%")
                .set("box-sizing", "border-box");

        card.add(buildHeader(), divider());
        card.add(infoRow("用户名", user.getUsername(), false, null, false));
        card.add(infoRow("手机号", display(maskPhone(user.getPhone())), StrUtil.isBlank(user.getPhone()),
                editButton(this::openPhoneDialog), false));
        card.add(infoRow("邮箱", display(maskEmail(user.getEmail())), StrUtil.isBlank(user.getEmail()),
                editButton(this::openEmailDialog), false));
        card.add(infoRow("密码", "****** 已设置", false,
                editButton(this::openPasswordDialog), true));
        add(card);
    }

    /**
     * 头部：居中头像 + 用户名 + 修改头像入口
     */
    private Component buildHeader() {
        Button editAvatar = new Button("修改头像", e -> openAvatarDialog());
        editAvatar.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        Span name = new Span(user.getUsername());
        name.getStyle().set("font-size", "var(--lumo-font-size-l)").set("font-weight", "600");

        VerticalLayout header = new VerticalLayout(
                AgentAvatar.create(user.getAvatar(), user.getUsername(), 96), name, editAvatar);
        header.setPadding(false);
        header.setSpacing(false);
        header.getStyle().set("gap", "var(--lumo-space-s)");
        header.setAlignItems(Alignment.CENTER);
        header.setWidthFull();
        return header;
    }

    private Component divider() {
        Div divider = new Div();
        divider.getStyle()
                .set("border-top", "1px solid var(--lumo-contrast-10pct)")
                .set("margin", "var(--lumo-space-l) 0 var(--lumo-space-s)");
        return divider;
    }

    /**
     * 信息行：左侧标签 + 值，右侧修改按钮；last=true 时不画底线
     */
    private Component infoRow(String label, String value, boolean unset, Button action, boolean last) {
        Span labelSpan = new Span(label);
        labelSpan.getStyle().set("color", "var(--lumo-secondary-text-color)").set("width", "5em");
        Span valueSpan = new Span(value);
        if (unset) {
            valueSpan.getStyle().set("color", "var(--lumo-tertiary-text-color)");
        }
        HorizontalLayout row = new HorizontalLayout(labelSpan, valueSpan);
        row.setWidthFull();
        row.expand(valueSpan);
        row.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        row.getStyle().set("padding", "var(--lumo-space-m) 0");
        if (!last) {
            row.getStyle().set("border-bottom", "1px solid var(--lumo-contrast-5pct)");
        }
        if (action != null) {
            row.add(action);
        }
        return row;
    }

    private Button editButton(Runnable onClick) {
        Button edit = new Button("修改", e -> onClick.run());
        edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        return edit;
    }

    private static String display(String masked) {
        return masked == null ? "未设置" : masked;
    }

    /**
     * 手机号脱敏：保留前 3 后 4，如 198****4847
     */
    private static String maskPhone(String phone) {
        if (StrUtil.isBlank(phone)) {
            return null;
        }
        return phone.length() >= 7
                ? phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4)
                : "****";
    }

    /**
     * 邮箱脱敏：保留首字符与域名，如 5******5@qq.com -> 5******@qq.com
     */
    private static String maskEmail(String email) {
        if (StrUtil.isBlank(email)) {
            return null;
        }
        int at = email.indexOf('@');
        return at <= 0 ? "******" : email.charAt(0) + "******" + email.substring(at);
    }

    // ==================== 修改弹窗 ====================

    private void openPhoneDialog() {
        TextField phone = new TextField("手机号");
        phone.setPlaceholder("11 位手机号，留空表示不设置");
        phone.setMaxLength(32);
        phone.setWidthFull();

        Binder<SysUser> binder = new Binder<>(SysUser.class);
        binder.forField(phone).withValidator(FormValidators.mobile())
                .asRequired("手机号码不能为空")
                .bind(SysUser::getPhone, SysUser::setPhone);
        binder.readBean(user);

        openEditDialog("修改手机号", phone, "保存", () -> {
            if (!binder.writeBeanIfValid(user)) {
                return false;
            }
            sysUserService.updateProfile(user.getId(), user.getPhone().trim(), user.getEmail());
            reload();
            return true;
        });
    }

    private void openEmailDialog() {
        TextField email = new TextField("邮箱");
        email.setPlaceholder("name@example.com，留空表示不设置");
        email.setMaxLength(128);
        email.setWidthFull();

        Binder<SysUser> binder = new Binder<>(SysUser.class);
        binder.forField(email).withValidator(FormValidators.email())
                .asRequired("邮箱不能为空")
                .bind(SysUser::getEmail, SysUser::setEmail);
        binder.readBean(user);

        openEditDialog("修改邮箱", email, "保存", () -> {
            if (!binder.writeBeanIfValid(user)) {
                return false;
            }
            sysUserService.updateProfile(user.getId(), user.getPhone(), user.getEmail().trim());
            reload();
            return true;
        });
    }

    private void openAvatarDialog() {
        TextField url = new TextField("头像图片 URL");
        url.setPlaceholder("https://example.com/avatar.png，留空恢复默认");
        url.setValue(StrUtil.nullToEmpty(user.getAvatar()));
        url.setWidthFull();
        url.setValueChangeMode(ValueChangeMode.EAGER);

        // 实时预览
        Div preview = new Div(AgentAvatar.create(url.getValue(), user.getUsername(), 64));
        preview.getStyle().set("display", "flex").set("justify-content", "center");
        url.addValueChangeListener(e -> {
            preview.removeAll();
            preview.add(AgentAvatar.create(e.getValue(), user.getUsername(), 64));
        });

        VerticalLayout body = new VerticalLayout(url, preview);
        body.setPadding(false);
        openEditDialog("修改头像", body, "保存", () -> {
            sysUserService.updateAvatar(user.getId(), url.getValue().trim());
            reload();
            return true;
        });
    }

    private void openPasswordDialog() {
        PasswordField oldPassword = new PasswordField("原密码");
        oldPassword.setWidthFull();
        PasswordField newPassword = new PasswordField("新密码");
        newPassword.setWidthFull();
        newPassword.setHelperText("至少 6 位");
        PasswordField confirmPassword = new PasswordField("确认新密码");
        confirmPassword.setWidthFull();

        Binder<PasswordForm> binder = new Binder<>(PasswordForm.class);
        binder.forField(oldPassword).asRequired("请输入原密码")
                .bind(PasswordForm::getOldPassword, PasswordForm::setOldPassword);
        binder.forField(newPassword).asRequired("请输入新密码")
                .withValidator(FormValidators.passwordMin(6))
                .bind(PasswordForm::getNewPassword, PasswordForm::setNewPassword);
        binder.forField(confirmPassword).asRequired("请再次输入新密码")
                .withValidator(v -> StrUtil.equals(v, newPassword.getValue()), "两次输入的新密码不一致")
                .bind(PasswordForm::getConfirmPassword, PasswordForm::setConfirmPassword);
        oldPassword.setRequiredIndicatorVisible(true);
        newPassword.setRequiredIndicatorVisible(true);
        confirmPassword.setRequiredIndicatorVisible(true);

        VerticalLayout body = new VerticalLayout(oldPassword, newPassword, confirmPassword);
        body.setPadding(false);
        openEditDialog("修改密码", body, "确认修改", () -> {
            PasswordForm form = new PasswordForm();
            if (!binder.writeBeanIfValid(form)) {
                return false;
            }
            sysUserService.changePassword(user.getId(), form.getOldPassword(), form.getNewPassword());
            reload();
            return true;
        });
    }

    /** 修改密码表单 */
    @Data
    private static class PasswordForm {
        private String oldPassword;
        private String newPassword;
        private String confirmPassword;
    }

    /**
     * 通用编辑弹窗：标题 + 内容 + 确认按钮；onConfirm 返回 false（表单校验未通过）或抛异常时
     * 提示并保持弹窗打开，返回 true 才关闭
     */
    private void openEditDialog(String title, Component content, String okText, BooleanSupplier onConfirm) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);
        dialog.setWidth("420px");
        dialog.add(content);

        Button cancel = new Button("取消", e -> dialog.close());
        Button ok = new Button(okText, e -> {
            boolean okToClose;
            try {
                okToClose = onConfirm.getAsBoolean();
            } catch (Exception ex) {
                Notify.error(ex.getMessage());
                return;
            }
            if (okToClose) {
                dialog.close();
            }
        });
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, ok);
        dialog.open();
    }

    /**
     * 保存成功后刷新页面，重新加载最新资料
     */
    private void reload() {
        Notify.success("已保存");
        UI.getCurrent().getPage().reload();
    }
}
