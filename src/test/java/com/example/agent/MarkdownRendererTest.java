package com.example.agent;

import com.example.agent.ui.chat.MarkdownRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Markdown 渲染单元测试：GFM 表格 + 原始 HTML 转义 */
class MarkdownRendererTest {

    @Test
    void rendersGfmTable() {
        String html = MarkdownRenderer.toHtml("| 字段 | 内容 |\n|---|---|\n| 活动ID | 2358 |");
        assertTrue(html.contains("<table>"), "管道表格应渲染为 <table>");
        assertTrue(html.contains("<th>字段</th>"));
        assertTrue(html.contains("<td>2358</td>"));
    }

    @Test
    void escapesRawHtml() {
        String html = MarkdownRenderer.toHtml("<script>alert(1)</script>");
        assertFalse(html.contains("<script>"), "原始 HTML 应被转义防注入");
    }
}
