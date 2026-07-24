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
    /** 可达性探测的连接超时（毫秒）：只做快速 TCP 探测，避免拖慢启动 */
    private static final int PROBE_TIMEOUT_MS = 3000;

    /**
     * 轻量可达性探测：TCP 能连上即视为可达。
     * 用于挂载/初始化前预检——MCP 服务不可达时直接跳过，
     * 避免 SDK 初始化失败在日志里刷大段错误堆栈。
     */
    public boolean reachable(McpServer server) {
        try {
            java.net.URI uri = java.net.URI.create(server.getUrl());
            int port = uri.getPort() == -1
                    ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80)
                    : uri.getPort();
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(uri.getHost(), port), PROBE_TIMEOUT_MS);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

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
        if (!reachable(server)) {
            return "无法连接 " + server.getUrl();
        }
        try (McpClientWrapper client = create(server)) {
            client.initialize().block();
            return null;
        } catch (Exception e) {
            return rootMessage(e);
        }
    }

    /** 从 MCP 服务实时拉取工具列表 */
    public List<McpSchema.Tool> listTools(McpServer server) {
        if (!reachable(server)) {
            throw new IllegalStateException("无法连接 " + server.getUrl());
        }
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
