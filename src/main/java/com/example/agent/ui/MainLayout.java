package com.example.agent.ui;

import com.example.agent.system.auth.LoginHelper;
import com.example.agent.system.auth.LoginUser;
import com.example.agent.ui.view.AgentView;
import com.example.agent.ui.view.ApiKeyView;
import com.example.agent.ui.view.ChatView;
import com.example.agent.ui.view.CustomToolView;
import com.example.agent.ui.view.DashboardView;
import com.example.agent.ui.view.KnowledgeView;
import com.example.agent.ui.view.McpServerView;
import com.example.agent.ui.view.ModelView;
import com.example.agent.ui.view.OperationLogView;
import com.example.agent.ui.view.SkillRepoView;
import com.example.agent.ui.view.StateStoreView;
import com.example.agent.ui.view.TokenMonitorView;
import com.example.agent.ui.view.ToolView;
import com.example.agent.ui.view.UserView;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;

import java.time.Year;

/**
 * 主布局：顶部栏 + 经典管理后台侧边导航。
 * 业务样式统一在这里全局加载（AppShellConfigurator 上的 @StyleSheet 在当前环境不生效，
 * RouterLayout 上的 @StyleSheet 对所有子路由生效）。
 */
@StyleSheet("context://styles/side-nav.css")
@StyleSheet("context://styles/app-grid.css")
@StyleSheet("context://styles/app-dialog.css")
@StyleSheet("context://styles/chat.css")
@StyleSheet("context://styles/markdown.css")
@StyleSheet("context://styles/agent-panel.css")
public class MainLayout extends AppLayout {

    private final Div footer = new Div();

    public MainLayout() {
        Icon logoIcon = new Icon(VaadinIcon.MAGIC);
        logoIcon.getStyle().set("color", "var(--lumo-primary-color)");
        H1 title = new H1("agent-platform");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)").set("margin", "0");
        HorizontalLayout logo = new HorizontalLayout(logoIcon, title);
        logo.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        // 侧边菜单收起 / 展开切换
        Button menuToggle = new Button(new Icon(VaadinIcon.MENU), e -> setDrawerOpened(!isDrawerOpened()));
        menuToggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout header = new HorizontalLayout(menuToggle, logo);
        header.setWidthFull();
        header.setPadding(true);
        header.expand(logo);
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        // 右侧：当前登录用户（点击进入个人主页）+ 退出
        LoginUser currentUser = LoginHelper.currentUser();
        if (currentUser != null) {
            Button account = new Button(currentUser.getUsername()
                    + (LoginHelper.isAdmin() ? "（管理员）" : ""),
                    new Icon(VaadinIcon.USER), e -> getUI().ifPresent(ui -> ui.navigate("profile")));
            account.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            Button logout = new Button("退出", new Icon(VaadinIcon.SIGN_OUT), e -> {
                LoginHelper.logout();
                getUI().ifPresent(ui -> ui.navigate("login"));
            });
            logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            header.add(account, logout);
        }
        addToNavbar(header);

        // 抽屉内容包一层滚动容器：滚动条默认隐藏，鼠标悬停菜单时才显示
        Div drawerContent = new Div();
        drawerContent.addClassName("drawer-scroll");
        drawerContent.add(
                section("概览"),
                navOf(item("首页", DashboardView.class, VaadinIcon.DASHBOARD),
                        item("Token监控", TokenMonitorView.class, VaadinIcon.BAR_CHART)),
                section("应用"),
                navOf(item("智能体", AgentView.class, VaadinIcon.CLUSTER),
                        item("流式对话", ChatView.class, VaadinIcon.CHAT)
                ),
                section("资源管理"),
                navOf(item("模型", ModelView.class, VaadinIcon.DATABASE),
                        item("系统工具", ToolView.class, VaadinIcon.TOOLS),
                        item("自定义工具", CustomToolView.class, VaadinIcon.EXTERNAL_LINK),
                        item("MCP服务", McpServerView.class, VaadinIcon.PLUG),
                        item("知识库", KnowledgeView.class, VaadinIcon.BOOK),
                        item("技能仓库", SkillRepoView.class, VaadinIcon.LIGHTBULB),
                        item("数据存储", StateStoreView.class, VaadinIcon.ARCHIVE),
                        item("ApiKey", ApiKeyView.class, VaadinIcon.KEY)));
        // 用户管理、操作日志仅管理员可见
        if (LoginHelper.isAdmin()) {
            drawerContent.add(section("系统"),
                    navOf(item("用户管理", UserView.class, VaadinIcon.USERS),
                            item("操作日志", OperationLogView.class, VaadinIcon.CLIPBOARD_TEXT)));
        }
        addToDrawer(drawerContent);

        // 主内容区底部居中的版权信息
        Span copyright = new Span("Copyright © " + Year.now().getValue() + " agent-platform 版权所有");
        footer.add(copyright);
        footer.getStyle()
                .set("text-align", "center")
                .set("padding", "var(--lumo-space-s)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)");
    }

    /**
     * 路由内容外包一层：视图占满剩余空间，版权信息固定在主页面底部居中
     */
    @Override
    public void showRouterLayoutContent(HasElement content) {
        Div viewContainer = new Div();
        viewContainer.getElement().appendChild(content.getElement());
        viewContainer.getStyle()
                .set("flex", "1 1 auto")
                .set("min-height", "0")
                .set("overflow", "auto");
        Div wrapper = new Div(viewContainer, footer);
        wrapper.setSizeFull();
        wrapper.getStyle().set("display", "flex").set("flex-direction", "column");
        setContent(wrapper);
    }

    private SideNavItem item(String label, Class<? extends com.vaadin.flow.component.Component> view, VaadinIcon icon) {
        SideNavItem item = new SideNavItem(label, view);
        item.setPrefixComponent(new Icon(icon));
        return item;
    }

    /**
     * 一组菜单项包一个 SideNav（分组标题 + 独立分组，便于分区间距控制）
     */
    private SideNav navOf(SideNavItem... items) {
        SideNav nav = new SideNav();
        nav.addItem(items);
        return nav;
    }

    /**
     * 分组小标题：灰字宽字距，与数据看板副标题风格一致
     */
    private Div section(String label) {
        Div section = new Div();
        section.setText(label);
        section.addClassName("side-nav-section");
        return section;
    }
}
