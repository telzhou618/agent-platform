package com.example.agent.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 单个智能体的 token 消耗统计（智能体排行数据来源） */
@Data
public class AgentTokenStat {

    /** 智能体 ID */
    private Long agentId;
    /** 智能体名称 */
    private String agentName;
    /** 最近使用的模型名称（快照） */
    private String modelName;
    /** 累计 token 总量 */
    private Long totalTokens;
    /** 累计输入 token */
    private Long inputTokens;
    /** 累计输出 token */
    private Long outputTokens;
    /** 累计缓存命中 token */
    private Long cachedTokens;
    /** 累计模型调用次数 */
    private Long callCount;
    /** 平均单次调用耗时（毫秒） */
    private Long avgDurationMs;
    /** 最近一次调用时间 */
    private LocalDateTime lastActiveTime;

    /** 缓存命中率（%）：cached / input，无输入时为 0 */
    public long getCacheHitRate() {
        if (inputTokens == null || inputTokens == 0 || cachedTokens == null) {
            return 0;
        }
        return Math.round(cachedTokens * 100.0 / inputTokens);
    }
}
