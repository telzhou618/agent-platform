package com.example.agent.ui.view;

import cn.hutool.core.util.StrUtil;
import com.example.agent.system.auth.LoginHelper;
import com.example.agent.system.entity.SysUser;
import com.example.agent.system.service.SysUserService;
import com.example.agent.ui.MainLayout;
import com.example.agent.ui.component.Notify;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * 个人主页（简约风格）：查看账号信息，修改手机号 / 邮箱（仅校验格式），
 * 修改密码（需验证原密码）。右上角点击账号进入。
 */
@Route(value = "profile", layout = MainLayout.class)
@PageTitle("个人主页 - agent-platform")
public class ProfileView extends VerticalLayout {

    private final SysUserService sysUserService;

    public ProfileView(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
        setSizeFull();
        setMaxWidth("640px");

        H2 title = new H2("个人主页");
        title.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-xl)");

        add(title, buildProfileCard(), buildPasswordCard());
    }

    /** 基本资料卡片：用户名只读，手机号 / 邮箱可改（仅格式校验） */
    private Div buildProfileCard() {
        SysUser user = currentUser();

        TextField username = new TextField("用户名");
        username.setValue(StrUtil.nullToEmpty(user.getUsername()));
        username.setReadOnly(true);
        username.setWidthFull();

        TextField phone = new TextField("手机号");
        phone.setValue(StrUtil.nullToEmpty(user.getPhone()));
        phone.setWidthFull();

        EmailField email = new EmailField("邮箱");
        email.setValue(StrUtil.nullToEmpty(user.getEmail()));
        email.setWidthFull();

        Button save = new Button("保存资料", e -> {
            try {
                sysUserService.updateProfile(user.getId(), phone.getValue().trim(), email.getValue().trim());
                Notify.success("资料已保存");
            } catch (Exception ex) {
                Notify.error(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        return card("基本资料", new Div(username, phone, email, save));
    }

    /** 修改密码卡片：原密码 + 新密码 + 确认新密码 */
    private Div buildPasswordCard() {
        SysUser user = currentUser();

        PasswordField oldPassword = new PasswordField("原密码");
        oldPassword.setWidthFull();
        PasswordField newPassword = new PasswordField("新密码");
        newPassword.setWidthFull();
        PasswordField confirmPassword = new PasswordField("确认新密码");
        confirmPassword.setWidthFull();

        Button save = new Button("修改密码", e -> {
            if (!newPassword.getValue().equals(confirmPassword.getValue())) {
                Notify.error("两次输入的新密码不一致");
                return;
            }
            try {
                sysUserService.changePassword(user.getId(), oldPassword.getValue(), newPassword.getValue());
                oldPassword.clear();
                newPassword.clear();
                confirmPassword.clear();
                Notify.success("密码已修改");
            } catch (Exception ex) {
                Notify.error(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        return card("修改密码", new Div(oldPassword, newPassword, confirmPassword, save));
    }

    /** 白底卡片容器：标题 + 内容 */
    private Div card(String title, Div content) {
        H3 cardTitle = new H3(title);
        cardTitle.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-l)");
        content.getStyle().set("display", "flex").set("flex-direction", "column")
                .set("gap", "var(--lumo-space-m)");
        Div card = new Div(cardTitle, content);
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "12px")
                .set("padding", "var(--lumo-space-l)")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--lumo-space-m)")
                .set("width", "100%")
                .set("box-sizing", "border-box");
        return card;
    }

    private SysUser currentUser() {
        return sysUserService.getById(LoginHelper.currentUserId());
    }
}
