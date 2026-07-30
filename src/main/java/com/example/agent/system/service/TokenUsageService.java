package com.example.agent.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.agent.system.auth.LoginHelper;
import com.example.agent.system.dto.AgentTokenStat;
import com.example.agent.system.dto.DailyTokenUsage;
import com.example.agent.system.dto.TokenOverviewStat;
import com.example.agent.system.entity.AgentInfo;
import com.example.agent.system.entity.AgentTokenUsage;
import com.example.agent.system.entity.ModelConfig;
import com.example.agent.system.mapper.AgentTokenUsageMapper;
import io.agentscope.core.model.ChatUsage;
import lombok.RequiredArgsConstructor;
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

/**
 * 智能体 token 消耗：事件流中捕获 ModelCallEndEvent 后异步落库（每次模型调用一条），
 * 以及 token 监控页的统计查询。管理端（ChatService）与开放接口（AgentProxyService）共用本服务，
 * 通过 source 区分来源。注意只有服务商上报了 usage 的调用才会落库（流式末帧无 usage 时跳过）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenUsageService extends ServiceImpl<AgentTokenUsageMapper, AgentTokenUsage> {

    private final AgentInfoService agentInfoService;
    private final ModelConfigService modelConfigService;

    /**
     * 记录一次模型调用的 token 消耗。异步落库：埋点不阻塞流式输出，DB 异常也不影响对话。
     */
    public void record(Long agentId, String sessionId, String userId, String source, ChatUsage usage) {
        if (usage == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                AgentTokenUsage record = new AgentTokenUsage();
                record.setAgentId(agentId);
                record.setModelName(resolveModelName(agentId));
                record.setSessionId(sessionId);
                record.setUserId(userId);
                record.setSource(source);
                record.setInputTokens(usage.getInputTokens());
                record.setOutputTokens(usage.getOutputTokens());
                record.setCachedTokens(usage.getCachedTokens());
                record.setTotalTokens(usage.getTotalTokens());
                // ChatUsage.time 单位是秒，统一存毫秒
                record.setDurationMs(Math.round(usage.getTime() * 1000));
                save(record);
            } catch (Exception e) {
                log.warn("token 消耗落库失败：agentId={} sessionId={}", agentId, sessionId, e);
            }
        });
    }

    /** token 消耗概览，普通用户只看自己智能体的数据 */
    public TokenOverviewStat overview() {
        TokenOverviewStat stat = baseMapper.overview(LocalDate.now().atStartOfDay(), scopeUserId());
        return stat != null ? stat : new TokenOverviewStat();
    }

    /** 近 N 日 token 趋势：补齐没有数据的日期，返回恰好 days 天（旧 -> 新），普通用户只看自己智能体的数据 */
    public List<DailyTokenUsage> trend(int days) {
        LocalDate today = LocalDate.now();
        LocalDate since = today.minusDays(days - 1L);
        Map<LocalDate, DailyTokenUsage> byDate = baseMapper.dailyUsage(since.atStartOfDay(), scopeUserId())
                .stream()
                .collect(Collectors.toMap(DailyTokenUsage::getDate, Function.identity()));
        return IntStream.range(0, days)
                .mapToObj(since::plusDays)
                .map(date -> byDate.getOrDefault(date, new DailyTokenUsage(date, 0L, 0L)))
                .toList();
    }

    /** 按智能体聚合的 token 消耗（按总量倒序），普通用户只看自己智能体的数据 */
    public List<AgentTokenStat> agentStats() {
        return baseMapper.agentStats(scopeUserId());
    }

    /** 消耗明细分页（按时间倒序），可按智能体 / 来源过滤；普通用户只看自己智能体的数据 */
    public Page<AgentTokenUsage> pageRecords(Long agentId, String source, int page, int pageSize) {
        return lambdaQuery()
                .eq(agentId != null, AgentTokenUsage::getAgentId, agentId)
                .eq(source != null && !source.isBlank(), AgentTokenUsage::getSource, source)
                .inSql(scopeUserId() != null, AgentTokenUsage::getAgentId,
                        "select id from agent_info where user_id = " + scopeUserId())
                .orderByDesc(AgentTokenUsage::getId)
                .page(new Page<>(page, pageSize));
    }

    /** 模型名称快照：agent -> modelId -> model_config.name，解析失败返回 null 不影响落库 */
    private String resolveModelName(Long agentId) {
        AgentInfo agent = agentInfoService.getById(agentId);
        if (agent == null || agent.getModelId() == null) {
            return null;
        }
        ModelConfig model = modelConfigService.getById(agent.getModelId());
        return model == null ? null : model.getName();
    }

    /** 统计数据权限范围：管理员看全部（null 不过滤），普通用户只统计自己名下的智能体 */
    private Long scopeUserId() {
        return LoginHelper.isAdmin() ? null : LoginHelper.currentUserId();
    }
}
