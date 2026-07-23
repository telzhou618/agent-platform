package com.example.agent.ui;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Markdown -> HTML 渲染器。转义原始 HTML，防止模型输出注入页面。
 * Parser / HtmlRenderer 均线程安全，全局共用一份。
 */
public final class MarkdownRenderer {

    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().escapeHtml(true).build();

    private MarkdownRenderer() {
    }

    public static String toHtml(String markdown) {
        return RENDERER.render(PARSER.parse(markdown));
    }
}
