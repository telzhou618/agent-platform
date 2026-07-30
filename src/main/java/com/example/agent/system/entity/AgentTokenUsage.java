package com.example.agent.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 智能体 token 消耗记录：每次模型调用落一条，token 监控数据来源 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_token_usage")
public class AgentTokenUsage extends BaseEntity {

    /** 来源：管理端对话（ChatService） */
    public static final String SOURCE_ADMIN = "admin";
    /** 来源：开放接口（AgentProxyService） */
    public static final String SOURCE_API = "api";

    /** 智能体 ID（agent_info.id） */
    private Long agentId;

    /** 模型名称快照（记录时从 agent 关联模型解析，可能为空） */
    private String modelName;

    /** 会话 ID */
    private String sessionId;

    /** 调用方用户 ID（管理端登录用户 / API 调用方 userId） */
    private String userId;

    /** 来源：admin 管理端对话，api 开放接口 */
    private String source;

    /** 输入 token 数 */
    private Integer inputTokens;

    /** 输出 token 数 */
    private Integer outputTokens;

    /** 缓存命中 token 数（inputTokens 的子集） */
    private Integer cachedTokens;

    /** 总 token 数（input + output） */
    private Integer totalTokens;

    /** 模型调用耗时（毫秒） */
    private Long durationMs;
}
