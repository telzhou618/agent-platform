package com.example.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 智能体会话状态存储（AgentStateStore）配置，前缀 agent-platform.state-store */
@Data
@Component
@ConfigurationProperties("agent-platform.state-store")
public class StateStoreProperties {

    /** JsonFile 根目录（每智能体一个子目录隔离） */
    private String jsonDir = "workspaces/state";

    /** Redis key 前缀（后拼 agent-<id> 隔离） */
    private String redisKeyPrefix = "agent-platform:state";

    /** MySQL 状态库名（不存在自动创建） */
    private String mysqlDatabase = "agentscope";

    /** MySQL 共用表名（不存在自动创建） */
    private String mysqlTable = "agentscope_sessions";
}
