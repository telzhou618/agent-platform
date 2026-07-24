package com.example.agent.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** MCP 服务 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mcp_server")
public class McpServer extends BaseEntity {

    /** 传输类型：可流式传输的 HTTP */
    public static final String TYPE_STREAMABLE_HTTP = "streamableHttp";
    /** 传输类型：SSE */
    public static final String TYPE_SSE = "sse";

    /** MCP 服务名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 传输类型：streamableHttp / sse */
    private String type;

    /** 服务地址 */
    private String url;

    /** 请求头，JSON 对象 {key:value} */
    private String headers;

    /** 超时时间（毫秒） */
    private Integer timeout;
}
