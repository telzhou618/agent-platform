package com.example.agent.ui;

import com.example.agent.system.auth.LoginHelper;
import com.example.agent.system.entity.SysUser;
import com.example.agent.system.service.SysUserService;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.Random;

/** 登录页：独立全屏页面（不挂主布局），背景图每次刷新随机切换 */
@Route("login")
@PageTitle("登录 - agent-platform")
@StyleSheet("context://styles/login.css")
public class LoginView extends VerticalLayout {

    /** 背景图数量，位于 META-INF/resources/images/login/ */
    private static final int BG_COUNT = 4;

    private final SysUserService sysUserService;

    private final TextField username = new TextField("用户名");
    private final PasswordField password = new PasswordField("密码");
    private final Div errorTip = new Div();

    public LoginView(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
        addClassName("login-view");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        // 每次刷新随机一张背景图
        int index = new Random().nextInt(BG_COUNT) + 1;
        getStyle().set("background-image", "url('images/login/bg-" + index + ".jpg')");

        H1 title = new H1("agent-platform");
        Paragraph subtitle = new Paragraph("AgentScope 智能体管理平台");
        subtitle.addClassName("login-subtitle");

        username.setWidthFull();
        username.setPrefixComponent(new Icon(VaadinIcon.USER));
        password.setWidthFull();
        password.setPrefixComponent(new Icon(VaadinIcon.LOCK));

        errorTip.addClassName("login-error");
        errorTip.setVisible(false);

        Button login = new Button("登 录", e -> login());
        login.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        login.setWidthFull();
        login.addClickShortcut(Key.ENTER);

        VerticalLayout card = new VerticalLayout(title, subtitle, username, password, errorTip, login);
        card.addClassName("login-card");
        card.setPadding(true);
        card.setSpacing(true);
        card.setWidth("380px");
        card.setAlignItems(Alignment.CENTER);

        add(card);
    }

    private void login() {
        SysUser user = sysUserService.authenticate(username.getValue().trim(), password.getValue());
        if (user == null) {
            errorTip.setText("用户名或密码错误");
            errorTip.setVisible(true);
            password.clear();
            return;
        }
        LoginHelper.login(user);
        getUI().ifPresent(ui -> ui.navigate(""));
    }
}
