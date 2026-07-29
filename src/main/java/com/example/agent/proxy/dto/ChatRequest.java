package com.example.agent.proxy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 流式对话请求体
 */
@Data
@Schema(title = "流式对话请求")
public class ChatRequest {

    @Schema(title = "智能体 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long agentId;

    @Schema(title = "用户 ID（外部系统用户标识，会话按 userId+sessionId 隔离）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String userId;

    @Schema(title = "会话 ID（相同 ID 延续上下文，换新 ID 即新开会话）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String sessionId;

    @Schema(title = "用户消息", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;
}
