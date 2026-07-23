package com.example.agent.ui;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "", layout = MainLayout.class)
@PageTitle("首页 - agent-platform")
public class HomeView extends VerticalLayout {

    public HomeView() {
        H2 title = new H2("Agent 管理平台");
        title.getStyle().set("margin", "0");

        Paragraph intro = new Paragraph("基于 AgentScope + Spring Boot + Vaadin 的综合性 Agent 管理平台。");
        Paragraph guide = new Paragraph("通过左侧菜单管理模型、智能体和工具："
                + "「模型管理」维护各家大模型配置，「智能体管理」编排提示词与工具，「工具管理」查看系统中的 @Tool 工具。");
        intro.getStyle().set("color", "var(--lumo-secondary-text-color)");
        guide.getStyle().set("color", "var(--lumo-secondary-text-color)");

        add(title, intro, guide);
    }
}
