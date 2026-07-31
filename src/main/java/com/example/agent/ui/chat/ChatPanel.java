package com.example.agent.ui.chat;

import cn.hutool.core.util.StrUtil;
import com.example.agent.system.chat.ChatService;
import com.example.agent.system.entity.AgentInfo;
import com.example.agent.ui.component.Notify;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;

import java.util.List;

/**
 * 流式对话面板：智能体选择 + 消息区 + 输入框，ChatView（整页）与 AgentView 的对话弹窗共用。
 * 切换智能体或点击「新会话」都会清空消息并生成新的 sessionId（服务端会话历史随之隔离）。
 */
public class ChatPanel extends VerticalLayout {

    private final transient ChatService chatService;

    private final Select<AgentInfo> agentSelect = new Select<>();
    private final Span sessionHint = new Span();
    private final VerticalLayout messages = new VerticalLayout();
    private final Scroller scroller;
    private final MessageInput input = new MessageInput();

    /**
     * 可选智能体列表（仅启用状态，由调用方传入）
     */
    private final List<AgentInfo> agents;

    /**
     * 当前会话
     */
    private String sessionId;
    private AgentInfo currentAgent;

    /**
     * preselected 非空且在列表内时预选该智能体（对话弹窗场景），否则默认选第一个
     */
    public ChatPanel(List<AgentInfo> agents, ChatService chatService, AgentInfo preselected) {
        this.chatService = chatService;
        this.agents = agents;
        setSizeFull();

        agentSelect.setLabel("选择智能体");
        agentSelect.setWidth("240px");
        agentSelect.setItems(agents);
        agentSelect.setItemLabelGenerator(AgentInfo::getName);

        Button newSession = new Button("新会话", new Icon(VaadinIcon.REFRESH),
                e -> startSession(agentSelect.getValue()));
        newSession.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        sessionHint.addClassName("chat-session-badge");
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

        add(toolbar, scroller, input);
        expand(scroller);

        // 切换智能体即新开会话：清空消息 + 新 sessionId
        agentSelect.addValueChangeListener(e -> startSession(e.getValue()));

        // 初始选中：优先预选（按 ID 匹配，列表对象与传入对象未必是同一实例），否则第一个
        AgentInfo initial = preselected == null ? null : agents.stream()
                .filter(a -> a.getId().equals(preselected.getId()))
                .findFirst().orElse(null);
        if (initial == null && !agents.isEmpty()) {
            initial = agents.get(0);
        }
        if (initial != null) {
            agentSelect.setValue(initial);
        } else {
            startSession(null);
        }
    }

    /**
     * 按 ID 切换选中的智能体（ChatView 的 URL 参数入口）；null 或未匹配时不改变当前选择
     */
    public void selectAgentById(Long agentId) {
        if (agentId == null) {
            return;
        }
        agents.stream()
                .filter(a -> a.getId().equals(agentId))
                .findFirst()
                .ifPresent(agentSelect::setValue);
    }

    /**
     * 新开会话：清空消息，生成新的 sessionId
     */
    private void startSession(AgentInfo agent) {
        this.currentAgent = agent;
        this.sessionId = chatService.newSessionId();
        messages.removeAll();
        sessionHint.setText("会话 " + sessionId.substring(0, Math.min(8, sessionId.length())));
        sessionHint.setTitle(sessionId);
    }

    private void send(String text) {
        if (StrUtil.isBlank(text)) {
            return;
        }
        addUserBubble(text);
        String agentName = currentAgent == null ? "AI" : currentAgent.getName();
        AssistantMessageView reply = new AssistantMessageView(agentName,
                currentAgent == null ? "🤖" : currentAgent.getAvatar());
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
                            Notify.error("对话出错：" + error.getMessage());
                        }),
                        () -> ui.access(() -> input.setEnabled(true)));
    }

    /**
     * 用户消息气泡：浅灰底靠右
     */
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

    private void scrollToBottom() {
        scroller.getElement().executeJs("this.scrollTop = this.scrollHeight");
    }
}
