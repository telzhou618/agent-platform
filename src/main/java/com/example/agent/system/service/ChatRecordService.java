package com.example.agent.system.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.agent.system.auth.LoginHelper;
import com.example.agent.system.dto.AgentActivityStat;
import com.example.agent.system.dto.DailyCount;
import com.example.agent.system.entity.ChatRecord;
import com.example.agent.system.mapper.ChatRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** 对话记录：异步落库 + dashboard 统计查询 */
@Slf4j
@Service
public class ChatRecordService extends ServiceImpl<ChatRecordMapper, ChatRecord> {

    /** 近 7 天活跃榜展示数量 */
    private static final int TOP_LIMIT = 5;
    /** 趋势图天数 */
    private static final int TREND_DAYS = 7;

    /**
     * 记录一轮对话。异步落库：埋点不阻塞流式输出，DB 异常也不影响对话。
     */
    public void record(Long agentId, String sessionId, int toolCalls, long durationMs, boolean success) {
        CompletableFuture.runAsync(() -> {
            try {
                ChatRecord record = new ChatRecord();
                record.setAgentId(agentId);
                record.setSessionId(sessionId);
                record.setToolCalls(toolCalls);
                record.setDurationMs(durationMs);
                record.setSuccess(success ? 1 : 0);
                save(record);
            } catch (Exception e) {
                log.warn("对话记录落库失败：agentId={} sessionId={}", agentId, sessionId, e);
            }
        });
    }

    /** 最近活跃的智能体统计（TOP 5，按最近活跃时间倒序），普通用户只看自己智能体的数据 */
    public List<AgentActivityStat> topActiveAgents() {
        return baseMapper.topActiveAgents(LocalDateTime.now().minusDays(TREND_DAYS), TOP_LIMIT, scopeUserId());
    }

    /** 近 7 日对话趋势：补齐没有数据的日期，返回恰好 7 天（旧 -> 新），普通用户只看自己智能体的数据 */
    public List<DailyCount> weeklyTrend() {
        LocalDate today = LocalDate.now();
        LocalDate since = today.minusDays(TREND_DAYS - 1L);
        Map<LocalDate, DailyCount> byDate = baseMapper.dailyCounts(since.atStartOfDay(), scopeUserId()).stream()
                .collect(Collectors.toMap(DailyCount::getDate, Function.identity()));
        return IntStream.range(0, TREND_DAYS)
                .mapToObj(since::plusDays)
                .map(date -> byDate.getOrDefault(date, new DailyCount(date, 0L)))
                .toList();
    }

    /** 统计数据权限范围：管理员看全部（null 不过滤），普通用户只统计自己名下的智能体 */
    private Long scopeUserId() {
        return LoginHelper.isAdmin() ? null : LoginHelper.currentUserId();
    }
}
