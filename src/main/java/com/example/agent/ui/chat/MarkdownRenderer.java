package com.example.agent.ui.chat;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.List;

/**
 * Markdown -> HTML 渲染器。启用 GFM 表格扩展；转义原始 HTML，防止模型输出注入页面。
 * Parser / HtmlRenderer 均线程安全，全局共用一份。
 */
public final class MarkdownRenderer {

    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .escapeHtml(true)
            .build();

    private MarkdownRenderer() {
    }

    public static String toHtml(String markdown) {
        return RENDERER.render(PARSER.parse(markdown));
    }
}
