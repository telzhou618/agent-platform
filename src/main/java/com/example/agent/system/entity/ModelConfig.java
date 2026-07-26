package com.example.agent.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 模型配置 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("model_config")
public class ModelConfig extends BaseEntity {

    /** 模型名称（展示用） */
    private String name;

    /** 供应商：dashscope/kimi/deepseek/glm/minimax/openai/anthropic/custom 自定义 */
    private String provider;

    /** 模型标识，如 qwen-plus、gpt-4o、claude-sonnet-4 */
    private String model;

    /** API 地址，自定义供应商时必填 */
    private String baseUrl;

    /** API Key */
    private String apiKey;

    /** 可用状态（保存时真实调用验证）：1 可用 0 不可用 */
    private Integer available;

    /** 最近一次的不可用原因 */
    private String checkMsg;

    /** 备注 */
    private String remark;

    /** 创建人（sys_user.id），管理员可看全部 */
    @TableField(fill = FieldFill.INSERT)
    private Long userId;
}
