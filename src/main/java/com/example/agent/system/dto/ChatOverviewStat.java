package com.example.agent.system.dto;

import lombok.Data;

/** 对话数据概览（dashboard 顶部统计条用） */
@Data
public class ChatOverviewStat {

    /** 累计对话轮数 */
    private Long totalCount;

    /** 今日对话轮数 */
    private Long todayCount;

    /** 会话总数（distinct session_id） */
    private Long sessionCount;

    /** 工具调用总次数 */
    private Long toolCallCount;

    /** 平均每轮耗时（毫秒） */
    private Long avgDurationMs;

    /** 整体成功率（0-100） */
    private Long successRate;
}
