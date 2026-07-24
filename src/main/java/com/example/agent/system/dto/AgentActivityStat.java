package com.example.agent.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 智能体活跃统计（dashboard 最近活跃列表用） */
@Data
public class AgentActivityStat {

    /** 智能体 ID */
    private Long agentId;

    /** 智能体名称 */
    private String agentName;

    /** 最近活跃时间 */
    private LocalDateTime lastActiveTime;

    /** 累计对话轮数 */
    private Long totalCount;

    /** 近 7 天对话轮数 */
    private Long weekCount;

    /** 会话数（distinct session_id） */
    private Long sessionCount;

    /** 工具调用总次数 */
    private Long toolCallCount;

    /** 成功率（0-100） */
    private Long successRate;
}
