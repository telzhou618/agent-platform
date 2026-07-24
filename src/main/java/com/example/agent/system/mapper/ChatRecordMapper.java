package com.example.agent.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.agent.system.dto.AgentActivityStat;
import com.example.agent.system.dto.DailyCount;
import com.example.agent.system.entity.ChatRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatRecordMapper extends BaseMapper<ChatRecord> {

    /** 最近活跃的智能体统计：按最近活跃时间倒序取前 limit 个（已删除的智能体不计入） */
    @Select("""
            select r.agent_id                    as agentId,
                   a.name                        as agentName,
                   max(r.create_time)            as lastActiveTime,
                   count(*)                      as totalCount,
                   sum(case when r.create_time >= #{weekAgo} then 1 else 0 end) as weekCount,
                   count(distinct r.session_id)  as sessionCount,
                   sum(r.tool_calls)             as toolCallCount,
                   round(sum(r.success) * 100 / count(*)) as successRate
            from chat_record r
            join agent_info a on a.id = r.agent_id and a.deleted = 0
            where r.deleted = 0
            group by r.agent_id, a.name
            order by lastActiveTime desc
            limit #{limit}
            """)
    List<AgentActivityStat> topActiveAgents(@Param("weekAgo") LocalDateTime weekAgo,
                                            @Param("limit") int limit);

    /** 近几天每天的对话轮数（只有数据的日期，缺的天数由调用方补零） */
    @Select("""
            select date(create_time) as date, count(*) as count
            from chat_record
            where deleted = 0 and create_time >= #{since}
            group by date(create_time)
            order by date
            """)
    List<DailyCount> dailyCounts(@Param("since") LocalDateTime since);
}
