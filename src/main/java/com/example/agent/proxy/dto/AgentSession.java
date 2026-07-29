package com.example.agent.proxy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话列表项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(title = "会话")
public class AgentSession {

    @Schema(title = "用户 ID")
    private String userId;

    @Schema(title = "会话 ID")
    private String sessionId;

    /**
     * 摘要
     */
    @Schema(title = "摘要")
    private String summary;

    /**
     * 首次发送消息时间
     */
    @Schema(title = "首次发送消息时间")
    private String timestamp;

}
