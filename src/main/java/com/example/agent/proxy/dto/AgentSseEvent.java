package com.example.agent.proxy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式对话 SSE 事件：一条事件对应 AgentScope 事件流中的一个可观测单元
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(title = "流式对话事件")
public class AgentSseEvent {

    @Schema(title = "事件类型", allowableValues = {"agent_start", "thinking", "text_block", "tool_call",
            "tool_result", "agent_result", "agent_end"})
    private String type;

    @Schema(title = "文本增量或完整文本")
    private String content;

    @Schema(title = "消息角色")
    private String role;

    @Schema(title = "工具调用信息")
    private ToolCallInfo toolCall;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(title = "工具调用信息")
    public static class ToolCallInfo {

        @Schema(title = "工具调用 ID")
        private String toolCallId;

        @Schema(title = "工具名称")
        private String toolName;

        @Schema(title = "完整参数（JSON 字符串），tool_call 时使用")
        private String toolParams;

        @Schema(title = "工具调用结果（JSON 字符串），tool_result 时使用")
        private String toolResults;
    }
}
