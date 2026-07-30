package com.example.agent.system.dto;

import lombok.Data;

/** token 消耗概览（token 监控页顶部指标卡数据来源） */
@Data
public class TokenOverviewStat {

    /** 累计 token 总量 */
    private Long totalTokens;
    /** 今日 token 总量 */
    private Long todayTokens;
    /** 累计输入 token */
    private Long inputTokens;
    /** 累计输出 token */
    private Long outputTokens;
    /** 累计缓存命中 token（input 子集） */
    private Long cachedTokens;
    /** 累计模型调用次数（有 usage 上报的） */
    private Long totalCalls;
    /** 今日模型调用次数 */
    private Long todayCalls;
    /** 平均单次调用耗时（毫秒） */
    private Long avgDurationMs;
    /** 产生消耗的智能体数量 */
    private Long agentCount;

    /** 缓存命中率（%）：cached / input，无输入时为 0 */
    public long getCacheHitRate() {
        if (inputTokens == null || inputTokens == 0 || cachedTokens == null) {
            return 0;
        }
        return Math.round(cachedTokens * 100.0 / inputTokens);
    }
}
