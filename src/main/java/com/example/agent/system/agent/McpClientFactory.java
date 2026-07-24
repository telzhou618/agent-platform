package com.example.agent.system.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.agent.system.entity.McpServer;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 客户端工厂：按 MCP 服务配置创建 {@link McpClientWrapper}，
 * 并提供连接验证、工具拉取能力（管理页和 AgentRegistry 共用）。
 */
@Component
public class McpClientFactory {

    private static final int DEFAULT_TIMEOUT_MS = 30000;

    /**
     * 按配置创建 MCP 客户端（尚未初始化）。
     * 调用方负责 initialize / registerMcpClient，用完后 close。
     */
    public McpClientWrapper create(McpServer server) {
        Duration timeout = Duration.ofMillis(
                server.getTimeout() == null ? DEFAULT_TIMEOUT_MS : server.getTimeout());
        McpClientBuilder builder = McpClientBuilder.create("mcp-" + server.getId())
                .timeout(timeout)
                .initializationTimeout(timeout);
        if (McpServer.TYPE_SSE.equals(server.getType())) {
            builder.sseTransport(server.getUrl());
        } else {
            builder.streamableHttpTransport(server.getUrl());
        }
        Map<String, String> headers = parseHeaders(server.getHeaders());
        if (!headers.isEmpty()) {
            builder.headers(headers);
        }
        return builder.buildSync();
    }

    /** 连接验证：返回 null 表示可用，否则返回错误信息 */
    public String testConnection(McpServer server) {
        try (McpClientWrapper client = create(server)) {
            client.initialize().block();
            return null;
        } catch (Exception e) {
            return rootMessage(e);
        }
    }

    /** 从 MCP 服务实时拉取工具列表 */
    public List<McpSchema.Tool> listTools(McpServer server) {
        try (McpClientWrapper client = create(server)) {
            client.initialize().block();
            return client.listTools().block();
        }
    }

    /** headers JSON 对象字符串 -> 有序 Map */
    public Map<String, String> parseHeaders(String headersJson) {
        if (StrUtil.isBlank(headersJson)) {
            return Map.of();
        }
        JSONObject obj = JSONUtil.parseObj(headersJson);
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : obj.keySet()) {
            map.put(key, obj.getStr(key));
        }
        return map;
    }

    /** 取最底层原因的错误信息，展示更直观 */
    private String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? e.getMessage() : cause.getMessage();
    }
}
