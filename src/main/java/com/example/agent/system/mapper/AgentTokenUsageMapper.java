package com.example.agent.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.agent.system.dto.AgentTokenStat;
import com.example.agent.system.dto.DailyTokenUsage;
import com.example.agent.system.dto.TokenOverviewStat;
import com.example.agent.system.entity.AgentTokenUsage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface AgentTokenUsageMapper extends BaseMapper<AgentTokenUsage> {

    /**
     * token 消耗概览：累计/今日 token、输入/输出/缓存、调用次数、平均耗时、活跃智能体数。
     * userId 不为 null 时只统计该用户名下智能体的记录（管理员传 null 看全部）。
     */
    @Select("""
            <script>
            select coalesce(sum(u.total_tokens), 0)              as totalTokens,
                   coalesce(sum(case when u.create_time &gt;= #{todayStart} then u.total_tokens else 0 end), 0) as todayTokens,
                   coalesce(sum(u.input_tokens), 0)              as inputTokens,
                   coalesce(sum(u.output_tokens), 0)             as outputTokens,
                   coalesce(sum(u.cached_tokens), 0)             as cachedTokens,
                   count(*)                                      as totalCalls,
                   sum(case when u.create_time &gt;= #{todayStart} then 1 else 0 end) as todayCalls,
                   coalesce(round(avg(u.duration_ms)), 0)        as avgDurationMs,
                   count(distinct u.agent_id)                    as agentCount
            from agent_token_usage u
            <if test="userId != null"> join agent_info a on a.id = u.agent_id and a.user_id = #{userId} </if>
            where u.deleted = 0
            </script>
            """)
    TokenOverviewStat overview(@Param("todayStart") LocalDateTime todayStart, @Param("userId") Long userId);

    /**
     * 近几天每天的输入/输出 token（只有数据的日期，缺的天数由调用方补零）。
     * userId 不为 null 时只统计该用户名下智能体的记录（管理员传 null 看全部）。
     */
    @Select("""
            <script>
            select date(u.create_time)        as date,
                   sum(u.input_tokens)        as inputTokens,
                   sum(u.output_tokens)       as outputTokens
            from agent_token_usage u
            <if test="userId != null"> join agent_info a on a.id = u.agent_id and a.user_id = #{userId} </if>
            where u.deleted = 0 and u.create_time &gt;= #{since}
            group by date(u.create_time)
            order by date
            </script>
            """)
    List<DailyTokenUsage> dailyUsage(@Param("since") LocalDateTime since, @Param("userId") Long userId);

    /**
     * 按智能体聚合 token 消耗（按总量倒序，已删除的智能体不计入）。
     * userId 不为 null 时只统计该用户名下智能体的记录（管理员传 null 看全部）。
     */
    @Select("""
            <script>
            select u.agent_id                          as agentId,
                   a.name                              as agentName,
                   max(u.model_name)                   as modelName,
                   sum(u.total_tokens)                 as totalTokens,
                   sum(u.input_tokens)                 as inputTokens,
                   sum(u.output_tokens)                as outputTokens,
                   sum(u.cached_tokens)                as cachedTokens,
                   count(*)                            as callCount,
                   coalesce(round(avg(u.duration_ms)), 0) as avgDurationMs,
                   max(u.create_time)                  as lastActiveTime
            from agent_token_usage u
            join agent_info a on a.id = u.agent_id and a.deleted = 0
            where u.deleted = 0
            <if test="userId != null"> and a.user_id = #{userId} </if>
            group by u.agent_id, a.name
            order by totalTokens desc
            </script>
            """)
    List<AgentTokenStat> agentStats(@Param("userId") Long userId);
}
