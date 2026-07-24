package com.example.agent;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;

/** Vaadin 应用壳：开启服务端推送，流式对话的增量文本实时推到浏览器；全局加载业务样式 */
@Push
@StyleSheet("context://styles/chat.css")
@StyleSheet("context://styles/markdown.css")
@StyleSheet("context://styles/agent-panel.css")
public class AppShell implements AppShellConfigurator {
}
