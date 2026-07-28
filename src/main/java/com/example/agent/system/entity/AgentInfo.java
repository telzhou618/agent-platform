package com.example.agent.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 智能体 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_info")
public class AgentInfo extends BaseEntity {

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：禁用 */
    public static final int STATUS_DISABLED = 0;

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

    /** 技能仓库 ID 列表，JSON 数组 */
    private String skillRepos;

    /** 自定义工具 ID 列表，JSON 数组 */
    private String customTools;

    /** 状态：1 启用 0 禁用；存量数据为 null 时按启用处理 */
    private Integer status = STATUS_ENABLED;

    /** 会话状态存储：memory/jsonfile/redis/mysql，默认本地 JSON 文件 */
    private String stateStore = "jsonfile";

    /** 描述 */
    private String description;

    /** 是否启用（null 视为启用，兼容存量数据） */
    public boolean isEnabled() {
        return !Integer.valueOf(STATUS_DISABLED).equals(status);
    }

    /** 创建人（sys_user.id），管理员可看全部 */
    @TableField(fill = FieldFill.INSERT)
    private Long userId;
}
