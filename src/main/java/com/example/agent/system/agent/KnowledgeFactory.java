package com.example.agent.system.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.agent.system.agent.knowledge.BailianKnowledgeRetriever;
import com.example.agent.system.agent.knowledge.DifyKnowledgeRetriever;
import com.example.agent.system.agent.knowledge.KnowledgeRetriever;
import com.example.agent.system.entity.KnowledgeBase;
import io.agentscope.core.rag.integration.bailian.BailianConfig;
import io.agentscope.core.rag.integration.dify.DifyRAGConfig;
import io.agentscope.core.rag.integration.dify.RetrievalMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 按知识库配置（knowledge_base 表）构建平台自有的 KnowledgeRetriever。
 * config 列为类型相关的 JSON 对象，按 type 分支构建；缺少必填配置时抛异常，
 * 由调用方（AgentRegistry）按"单个失败只记日志跳过"策略处理。
 * 底层复用 AgentScope 扩展包中未废弃的 BailianClient / DifyRAGClient，
 * 不再触碰 2.0 已废弃的 Knowledge / BailianKnowledge / DifyKnowledge。
 */
@Slf4j
@Component
public class KnowledgeFactory {

    /**
     * 检索分数过滤的默认阈值（与原官方 RetrieveConfig 默认值一致）
     */
    private static final double DEFAULT_SCORE_THRESHOLD = 0.5;

    /** 按类型构建知识库检索器；类型未知或必填配置缺失时抛异常 */
    public KnowledgeRetriever fromConfig(KnowledgeBase kb) {
        JSONObject config = StrUtil.isBlank(kb.getConfig())
                ? new JSONObject() : JSONUtil.parseObj(kb.getConfig());
        return switch (StrUtil.nullToEmpty(kb.getType())) {
            case KnowledgeBase.TYPE_BAILIAN -> buildBailian(kb, config);
            case KnowledgeBase.TYPE_DIFY -> buildDify(kb, config);
            default -> throw new IllegalArgumentException("未知知识库类型 " + kb.getType());
        };
    }

    /** 阿里云百炼：accessKeyId/accessKeySecret/workspaceId/indexId 必填，endpoint 选填 */
    private KnowledgeRetriever buildBailian(KnowledgeBase kb, JSONObject config) {
        BailianConfig.Builder builder = BailianConfig.builder()
                .accessKeyId(required(config, "accessKeyId"))
                .accessKeySecret(required(config, "accessKeySecret"))
                .workspaceId(required(config, "workspaceId"))
                .indexId(required(config, "indexId"));
        if (StrUtil.isNotBlank(config.getStr("endpoint"))) {
            builder.endpoint(config.getStr("endpoint"));
        }
        if (kb.getRetrieveLimit() != null) {
            builder.denseSimilarityTopK(kb.getRetrieveLimit());
        }
        builder.enableReranking(config.getBool("enableReranking"));
        builder.enableRewrite(config.getBool("enableRewrite"));
        return new BailianKnowledgeRetriever(builder.build(), scoreThreshold(kb));
    }

    /** Dify：apiKey/datasetId 必填，baseUrl/retrievalMode/enableRerank 选填 */
    private KnowledgeRetriever buildDify(KnowledgeBase kb, JSONObject config) {
        DifyRAGConfig.Builder builder = DifyRAGConfig.builder()
                .apiKey(required(config, "apiKey"))
                .datasetId(required(config, "datasetId"))
                .retrievalMode(parseRetrievalMode(config.getStr("retrievalMode")));
        if (StrUtil.isNotBlank(config.getStr("baseUrl"))) {
            builder.apiBaseUrl(config.getStr("baseUrl"));
        }
        if (kb.getRetrieveLimit() != null) {
            builder.topK(kb.getRetrieveLimit());
        }
        if (kb.getScoreThreshold() != null) {
            builder.scoreThreshold(kb.getScoreThreshold());
        }
        builder.enableRerank(config.getBool("enableRerank"));
        return new DifyKnowledgeRetriever(builder.build(), scoreThreshold(kb));
    }

    /** 客户端分数过滤阈值：未配置时用默认值 0.5 */
    private double scoreThreshold(KnowledgeBase kb) {
        return kb.getScoreThreshold() != null ? kb.getScoreThreshold() : DEFAULT_SCORE_THRESHOLD;
    }

    /** 检索模式字符串 -> 枚举，空或非法值回退混合检索 */
    private RetrievalMode parseRetrievalMode(String mode) {
        if (StrUtil.isBlank(mode)) {
            return RetrievalMode.HYBRID_SEARCH;
        }
        try {
            return RetrievalMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            log.warn("未知 Dify 检索模式 {}，回退 HYBRID_SEARCH", mode);
            return RetrievalMode.HYBRID_SEARCH;
        }
    }

    /** 取必填配置项，缺失抛异常（由调用方记日志跳过该知识库） */
    private String required(JSONObject config, String key) {
        String value = config.getStr(key);
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("知识库配置缺少必填项 " + key);
        }
        return value;
    }
}
