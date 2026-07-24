package com.example.agent.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 对话记录：每轮对话（一次提问到回复结束）落一条，dashboard 统计数据来源 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_record")
public class ChatRecord extends BaseEntity {

    /** 智能体 ID（agent_info.id） */
    private Long agentId;

    /** 会话 ID */
    private String sessionId;

    /** 本轮工具调用次数 */
    private Integer toolCalls;

    /** 本轮耗时（毫秒） */
    private Long durationMs;

    /** 是否成功：1 成功 0 失败 */
    private Integer success;
}
