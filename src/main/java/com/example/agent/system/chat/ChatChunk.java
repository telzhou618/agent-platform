package com.example.agent.system.chat;

/**
 * 流式对话的输出单元：回复文本、思考过程、工具调用三类信息统一成一条流。
 *
 * @param kind  信息类型
 * @param name  工具名（仅工具类信息有值）
 * @param delta 增量内容：文本 / 思考 / 工具入参 / 工具结果的片段；
 *              {@link Kind#TOOL_CALL_END} 时为工具执行状态（success / error 等）
 */
public record ChatChunk(Kind kind, String name, String delta) {

    public enum Kind {
        /** 思考过程增量 */
        THINKING,
        /** 回复文本增量 */
        TEXT,
        /** 工具调用开始 */
        TOOL_CALL_START,
        /** 工具入参 JSON 增量 */
        TOOL_CALL_ARGS,
        /** 工具结果文本增量 */
        TOOL_RESULT,
        /** 工具调用结束 */
        TOOL_CALL_END
    }

    public static ChatChunk of(Kind kind, String delta) {
        return new ChatChunk(kind, null, delta);
    }

    public static ChatChunk tool(Kind kind, String name, String delta) {
        return new ChatChunk(kind, name, delta);
    }
}
