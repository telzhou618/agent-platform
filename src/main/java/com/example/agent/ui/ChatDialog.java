package com.example.agent.ui;

import cn.hutool.core.util.StrUtil;
import com.example.agent.system.agent.ChatService;
import com.example.agent.system.entity.AgentInfo;
import com.example.agent.system.service.AgentInfoService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.select.Select;

import java.util.List;

/**
 * 流式对话窗口：选择智能体进行对话，对话内容流式输出。
 * 切换智能体或点击「新会话」都会清空消息并生成新的 sessionId（服务端会话历史随之隔离）。
 */
public class ChatDialog extends Dialog {

    private final transient AgentInfoService agentInfoService;
    private final transient ChatService chatService;

    private final Select<AgentInfo> agentSelect = new Select<>();
    private final Span sessionHint = new Span();
    private final VerticalLayout messages = new VerticalLayout();
    private final Scroller scroller;
    private final MessageInput input = new MessageInput();

    /** 当前会话 */
    private String sessionId;
    private AgentInfo currentAgent;

    public ChatDialog(AgentInfoService agentInfoService, ChatService chatService) {
        this(agentInfoService, chatService, null);
    }

    /** @param preselected 预选智能体（智能体管理页「对话」按钮进入时传入），null 则默认选第一个 */
    public ChatDialog(AgentInfoService agentInfoService, ChatService chatService, AgentInfo preselected) {
        this.agentInfoService = agentInfoService;
        this.chatService = chatService;
        setHeaderTitle("流式对话");
        setWidth("760px");
        setHeight("640px");
        setResizable(true);
        setDraggable(true);

        agentSelect.setLabel("选择智能体");
        agentSelect.setWidth("240px");
        List<AgentInfo> agents = agentInfoService.list();
        agentSelect.setItems(agents);
        agentSelect.setItemLabelGenerator(AgentInfo::getName);

        Button newSession = new Button("新会话", new Icon(VaadinIcon.REFRESH),
                e -> startSession(agentSelect.getValue()));
        newSession.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        sessionHint.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)");
        HorizontalLayout toolbar = new HorizontalLayout(agentSelect, newSession, sessionHint);
        toolbar.setWidthFull();
        toolbar.expand(sessionHint);
        toolbar.setDefaultVerticalComponentAlignment(Alignment.END);

        messages.setSpacing(false);
        messages.setPadding(false);
        messages.getStyle().set("gap", "var(--lumo-space-s)");
        scroller = new Scroller(messages);
        scroller.setSizeFull();

        input.setWidthFull();
        input.addSubmitListener(e -> send(e.getValue()));

        VerticalLayout layout = new VerticalLayout(toolbar, scroller, input);
        layout.setSizeFull();
        layout.setPadding(false);
        layout.expand(scroller);
        add(layout);

        // 切换智能体即新开会话：清空消息 + 新 sessionId
        agentSelect.addValueChangeListener(e -> startSession(e.getValue()));
        // 按 ID 匹配预选智能体（列表是重新查的，直接比对象可能因字段变化匹配不上）
        AgentInfo target = preselected == null ? null : agents.stream()
                .filter(a -> a.getId().equals(preselected.getId()))
                .findFirst().orElse(null);
        if (target != null) {
            agentSelect.setValue(target);
        } else if (!agents.isEmpty()) {
            agentSelect.setValue(agents.get(0));
        } else {
            startSession(null);
        }
    }

    /** 新开会话：清空消息，生成新的 sessionId */
    private void startSession(AgentInfo agent) {
        this.currentAgent = agent;
        this.sessionId = chatService.newSessionId();
        messages.removeAll();
        String name = agent == null ? "全局默认助手" : agent.getName();
        sessionHint.setText("sessionId: " + sessionId);
        addSystemLine("已切换到「" + name + "」，新会话已开始");
    }

    private void send(String text) {
        if (StrUtil.isBlank(text)) {
            return;
        }
        addUserBubble(text);
        AssistantMessageView reply = new AssistantMessageView();
        messages.add(reply);
        scrollToBottom();
        UI ui = UI.getCurrent();
        input.setEnabled(false);
        chatService.streamChat(sessionId, currentAgent == null ? null : currentAgent.getId(), text)
                .subscribe(
                        chunk -> ui.access(() -> {
                            reply.accept(chunk);
                            scrollToBottom();
                        }),
                        error -> ui.access(() -> {
                            reply.showError("对话出错：" + error.getMessage());
                            input.setEnabled(true);
                            Notification.show("对话出错：" + error.getMessage(),
                                    3000, Notification.Position.MIDDLE);
                        }),
                        () -> ui.access(() -> input.setEnabled(true)));
    }

    /** 用户消息气泡：靠右主色 */
    private void addUserBubble(String text) {
        Div bubble = new Div();
        bubble.setText(text);
        bubble.addClassName("user-bubble");
        HorizontalLayout row = new HorizontalLayout(bubble);
        row.setWidthFull();
        row.setPadding(false);
        row.setJustifyContentMode(JustifyContentMode.END);
        messages.add(row);
        scrollToBottom();
    }

    /** 系统提示行：居中灰色小字 */
    private void addSystemLine(String text) {
        Span line = new Span(text);
        line.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)");
        HorizontalLayout row = new HorizontalLayout(line);
        row.setWidthFull();
        row.setPadding(false);
        row.setJustifyContentMode(JustifyContentMode.CENTER);
        messages.add(row);
        scrollToBottom();
    }

    private void scrollToBottom() {
        scroller.getElement().executeJs("this.scrollTop = this.scrollHeight");
    }
}
