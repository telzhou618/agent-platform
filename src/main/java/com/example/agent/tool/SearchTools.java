package com.example.agent.tool;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.XmlUtil;
import cn.hutool.http.HttpRequest;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 联网搜索示例工具（必应 RSS 免费接口，无需 Key，返回 XML 结果列表）
 */
@Component
public class SearchTools {

    /**
     * 必应 RSS 搜索地址（mkt=zh-CN 中文市场），免费无需 Key
     */
    private static final String SEARCH_URL = "https://cn.bing.com/search?format=rss&mkt=zh-CN&q=";
    /**
     * 需携带浏览器 UA，否则可能返回验证页而非结果
     */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";
    private static final int TIMEOUT_MS = 15_000;
    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 10;

    @Tool(name = "web_search", description = "根据关键词联网搜索，返回相关网页的标题、链接和摘要。"
            + "当需要查询最新资讯、资料或不确定的事实时使用。",
            readOnly = true, concurrencySafe = true)
    public String webSearch(
            @ToolParam(name = "keyword", description = "搜索关键词") String keyword,
            @ToolParam(name = "limit", description = "返回条数，1-10，默认 5", required = false) Integer limit) {
        if (StrUtil.isBlank(keyword)) {
            return "搜索关键词不能为空";
        }
        int count = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(MAX_LIMIT, limit));
        try {
            String url = SEARCH_URL + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String xml = HttpRequest.get(url)
                    .header("User-Agent", USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .execute()
                    .body();
            return parseResults(xml, count);
        } catch (Exception e) {
            return "搜索失败：" + e.getMessage();
        }
    }

    /**
     * 解析 RSS XML，按序号输出 标题/链接/摘要；响应异常或无结果时返回提示文本
     */
    private String parseResults(String xml, int count) {
        if (StrUtil.isBlank(xml) || !xml.contains("<item>")) {
            return "未找到相关结果";
        }
        Document doc = XmlUtil.parseXml(xml);
        NodeList items = doc.getElementsByTagName("item");
        if (items.getLength() == 0) {
            return "未找到相关结果";
        }
        StringBuilder sb = new StringBuilder();
        int size = Math.min(count, items.getLength());
        for (int i = 0; i < size; i++) {
            Element item = (Element) items.item(i);
            sb.append(i + 1).append(". ").append(text(item, "title")).append('\n')
                    .append("   链接：").append(text(item, "link")).append('\n')
                    .append("   摘要：").append(text(item, "description")).append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * 取 item 子元素文本，缺失返回空串
     */
    private String text(Element item, String tag) {
        NodeList nodes = item.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : StrUtil.nullToEmpty(nodes.item(0).getTextContent()).trim();
    }
}
