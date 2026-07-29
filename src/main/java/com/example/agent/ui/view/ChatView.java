package com.example.agent.ui.view;

import com.example.agent.system.chat.ChatService;
import com.example.agent.system.service.AgentInfoService;
import com.example.agent.ui.MainLayout;
import com.example.agent.ui.chat.ChatPanel;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * 流式对话视图：ChatPanel 的整页包装。
 * 支持 URL 参数预选智能体：chat/{agentId}。
 */
@Route(value = "chat", layout = MainLayout.class)
@PageTitle("流式对话 - agent-platform")
public class ChatView extends VerticalLayout implements HasUrlParameter<Long> {

    private final ChatPanel chatPanel;

    public ChatView(AgentInfoService agentInfoService, ChatService chatService) {
        setSizeFull();

        H2 title = new H2("流式对话");
        title.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-xl)");

        chatPanel = new ChatPanel(agentInfoService.listEnabled(), chatService, null);

        add(title, chatPanel);
        expand(chatPanel);
    }

    /**
     * 按 URL 参数预选智能体；无参数时保持面板默认选择（第一个）
     */
    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter Long agentId) {
        chatPanel.selectAgentById(agentId);
    }
}
