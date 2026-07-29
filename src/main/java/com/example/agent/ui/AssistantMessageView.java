package com.example.agent.ui;

import com.example.agent.system.chat.ChatChunk;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * 一条助手消息：左侧智能体头像，右侧按到达顺序排列思考面板、工具调用面板和
 * Markdown 文本气泡。{@link #accept(ChatChunk)} 消费流式增量并更新对应区块。
 */
class AssistantMessageView extends HorizontalLayout {

    private final VerticalLayout content = new VerticalLayout();

    private Details thinkingPanel;
    private Div thinkingContent;
    private final StringBuilder thinkingAcc = new StringBuilder();

    private Div textBubble;
    private final StringBuilder textAcc = new StringBuilder();

    private ToolCallPanel toolPanel;

    AssistantMessageView(String agentName) {
        Avatar avatar = new Avatar(agentName);
        avatar.setAbbreviation(abbr(agentName));
        avatar.addClassName("chat-avatar");
        content.setPadding(false);
        content.setSpacing(false);
        content.getStyle().set("gap", "var(--lumo-space-xs)");
        content.setWidthFull();
        add(avatar, content);
        setPadding(false);
        setWidthFull();
        setAlignItems(Alignment.START);
        expand(content);
    }

    /** 头像缩写：取名称首字符，无名（全局默认助手）用 AI */
    private static String abbr(String name) {
        return name == null || name.isBlank() ? "AI" : name.substring(0, 1);
    }

    void accept(ChatChunk chunk) {
        switch (chunk.kind()) {
            case THINKING -> appendThinking(chunk.delta());
            case TEXT -> appendText(chunk.delta());
            case TOOL_CALL_START -> startToolCall(chunk.name());
            case TOOL_CALL_ARGS -> {
                if (toolPanel != null) {
                    toolPanel.appendArgs(chunk.delta());
                }
            }
            case TOOL_RESULT -> {
                if (toolPanel != null) {
                    toolPanel.appendResult(chunk.delta());
                }
            }
            case TOOL_CALL_END -> {
                if (toolPanel != null) {
                    toolPanel.finish(chunk.delta());
                    toolPanel = null;
                }
            }
        }
    }

    void showError(String message) {
        Div error = new Div();
        error.setText(message);
        error.addClassName("assistant-error");
        content.add(error);
    }

    /** 思考过程：折叠面板，流式期间展开，开始输出其它内容时自动收起 */
    private void appendThinking(String delta) {
        if (thinkingPanel == null) {
            thinkingContent = new Div();
            thinkingContent.addClassName("thinking-content");
            thinkingPanel = new Details("思考过程", thinkingContent);
            thinkingPanel.setOpened(true);
            thinkingPanel.addClassName("thinking-panel");
            content.add(thinkingPanel);
        }
        thinkingAcc.append(delta);
        thinkingContent.setText(thinkingAcc.toString());
    }

    /** 回复文本：Markdown 渲染气泡 */
    private void appendText(String delta) {
        closeThinking();
        if (textBubble == null) {
            textBubble = new Div();
            textBubble.addClassNames("assistant-bubble", "markdown");
            content.add(textBubble);
        }
        textAcc.append(delta);
        textBubble.getElement().setProperty("innerHTML",
                MarkdownRenderer.toHtml(textAcc.toString()));
    }

    /** 工具调用开始：新建面板；后续文本另起气泡，保持块间顺序 */
    private void startToolCall(String name) {
        closeThinking();
        textBubble = null;
        textAcc.setLength(0);
        toolPanel = new ToolCallPanel(name);
        content.add(toolPanel);
    }

    private void closeThinking() {
        if (thinkingPanel != null) {
            thinkingPanel.setOpened(false);
        }
    }

    /** 一次工具调用：标题为工具名 + 执行状态，内容可展开查看入参和返回 */
    private static class ToolCallPanel extends Details {

        private final String name;
        private final StringBuilder args = new StringBuilder();
        private final StringBuilder result = new StringBuilder();
        private final Div body = new Div();
        private Div argsPre;
        private Div resultPre;

        ToolCallPanel(String name) {
            this.name = name;
            addClassName("tool-panel");
            setOpened(true);
            updateSummary("运行中");
            body.addClassName("tool-panel-body");
            setContent(body);
        }

        void appendArgs(String delta) {
            args.append(delta);
            if (argsPre == null) {
                argsPre = addSection("入参");
            }
            argsPre.setText(args.toString());
        }

        void appendResult(String delta) {
            result.append(delta);
            if (resultPre == null) {
                resultPre = addSection("返回");
            }
            resultPre.setText(result.toString());
        }

        void finish(String state) {
            boolean success = "success".equals(state);
            updateSummary(success ? "成功" : state);
            if (!success) {
                addClassName("tool-panel-error");
            }
            setOpened(false);
        }

        private void updateSummary(String state) {
            setSummaryText("工具调用：" + name + "（" + state + "）");
        }

        private Div addSection(String label) {
            Span caption = new Span(label);
            caption.addClassName("tool-panel-caption");
            Div pre = new Div();
            pre.addClassName("tool-panel-pre");
            body.add(caption, pre);
            return pre;
        }
    }
}
