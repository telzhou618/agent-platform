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

    /** 头像：emoji 表情或图片 URL */
    private String avatar;

    /** 关联模型 ID（model_config.id） */
    private Long modelId;

    /** 模型温度：0-2，默认 1.0，值越高输出越随机 */
    private Double temperature = 1.0;

    /** 上下文数：每次对话携带的历史消息条数，1-20，默认 5；超出的旧消息随窗口淘汰 */
    private Integer contextCount = 5;

    /** Top P：null 表示未启用（用模型默认），启用时范围 0.01-1.0 */
    private Double topP;

    /** 每次回复的最大 Token 数：null 表示未启用（用模型默认） */
    private Integer maxTokens;

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

    /** 会话状态存储：memory/jsonfile/redis/mysql，默认内存 */
    private String stateStore = "memory";

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
