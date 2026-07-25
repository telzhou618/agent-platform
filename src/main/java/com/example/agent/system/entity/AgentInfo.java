package com.example.agent.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 智能体 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_info")
public class AgentInfo extends BaseEntity {

    /** 智能体名称 */
    private String name;

    /** 关联模型 ID（model_config.id） */
    private Long modelId;

    /** 系统提示词 */
    private String sysPrompt;

    /** 工具名称列表，JSON 数组 */
    private String tools;

    /** MCP 服务 ID 列表，JSON 数组 */
    private String mcpServers;

    /** 知识库 ID 列表，JSON 数组 */
    private String knowledgeBases;

    /** 描述 */
    private String description;

    /** 创建人（sys_user.id），管理员可看全部 */
    private Long userId;
}
