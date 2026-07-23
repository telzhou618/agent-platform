package com.example.agent.ui;

import com.example.agent.system.agent.ChatService;
import com.example.agent.system.service.AgentInfoService;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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

/** 主布局：顶部栏 + 经典管理后台侧边导航 */
public class MainLayout extends AppLayout {

    private final Div footer = new Div();

    public MainLayout(AgentInfoService agentInfoService, ChatService chatService) {
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
        addToNavbar(header);

        SideNav nav = new SideNav();
        nav.addItem(item("首页", HomeView.class, VaadinIcon.HOME));
        nav.addItem(item("模型管理", ModelView.class, VaadinIcon.DATABASE));
        nav.addItem(item("智能体管理", AgentView.class, VaadinIcon.CLUSTER));
        nav.addItem(item("工具管理", ToolView.class, VaadinIcon.TOOLS));
        // 流式对话：点击弹起对话窗口。SideNavItem 没有服务端点击 API，
        // 用 DOM 事件监听实现，样式与其他菜单项完全一致
        SideNavItem chat = new SideNavItem("流式对话");
        chat.setPrefixComponent(new Icon(VaadinIcon.CHAT));
        chat.getElement().addEventListener("click",
                e -> new ChatDialog(agentInfoService, chatService).open());
        nav.addItem(chat);
        addToDrawer(nav);

        // 主内容区底部居中的版权信息
        Span copyright = new Span("Copyright © " + Year.now().getValue() + " agent-platform 版权所有");
        footer.add(copyright);
        footer.getStyle()
                .set("text-align", "center")
                .set("padding", "var(--lumo-space-s)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)");
    }

    /** 路由内容外包一层：视图占满剩余空间，版权信息固定在主页面底部居中 */
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
}
