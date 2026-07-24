package com.example.agent.ui;

import cn.hutool.core.util.StrUtil;
import com.example.agent.system.agent.ChatService;
import com.example.agent.system.entity.AgentInfo;
import com.example.agent.system.service.AgentInfoService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
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
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.List;

/**
 * 流式对话视图：选择智能体进行对话，对话内容流式输出。
 * 切换智能体或点击「新会话」都会清空消息并生成新的 sessionId（服务端会话历史随之隔离）。
 * 支持 URL 参数预选智能体：chat/{agentId}（智能体管理页「对话」按钮进入时带上）。
 */
@Route(value = "chat", layout = MainLayout.class)
@PageTitle("流式对话 - agent-platform")
public class ChatView extends VerticalLayout implements HasUrlParameter<Long> {

    private final transient ChatService chatService;

    private final Select<AgentInfo> agentSelect = new Select<>();
    private final Span sessionHint = new Span();
    private final VerticalLayout messages = new VerticalLayout();
    private final Scroller scroller;
    private final MessageInput input = new MessageInput();

    /** 可选智能体列表（进入视图时查一次，预选按 ID 匹配） */
    private final List<AgentInfo> agents;

    /** 当前会话 */
    private String sessionId;
    private AgentInfo currentAgent;

    public ChatView(AgentInfoService agentInfoService, ChatService chatService) {
        this.chatService = chatService;
        this.agents = agentInfoService.list();
        setSizeFull();

        H2 title = new H2("流式对话");
        title.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-xl)");

        agentSelect.setLabel("选择智能体");
        agentSelect.setWidth("240px");
        agentSelect.setItems(agents);
        agentSelect.setItemLabelGenerator(AgentInfo::getName);

        Button newSession = new Button("新会话", new Icon(VaadinIcon.REFRESH),
                e -> startSession(agentSelect.getValue()));
        newSession.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        sessionHint.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)");
        HorizontalLayout toolbar = new HorizontalLayout(title, agentSelect, newSession, sessionHint);
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
    }

    /** 按 URL 参数预选智能体（列表是构造时查的，直接比对象可能因字段变化匹配不上）；无参数默认选第一个 */
    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter Long agentId) {
        AgentInfo target = agentId == null ? null : agents.stream()
                .filter(a -> a.getId().equals(agentId))
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
        String agentName = currentAgent == null ? "AI" : currentAgent.getName();
        AssistantMessageView reply = new AssistantMessageView(agentName);
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

    /** 用户消息气泡：靠右主色，带用户头像 */
    private void addUserBubble(String text) {
        Div bubble = new Div();
        bubble.setText(text);
        bubble.addClassName("user-bubble");
        Avatar avatar = new Avatar("我");
        avatar.addClassName("chat-avatar");
        HorizontalLayout row = new HorizontalLayout(bubble, avatar);
        row.setWidthFull();
        row.setPadding(false);
        row.setAlignItems(Alignment.START);
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
