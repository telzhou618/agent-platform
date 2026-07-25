package com.example.agent.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** ApiKey（将来用于访问智能体） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("api_key")
public class ApiKey extends BaseEntity {

    /** ApiKey 名称 */
    private String name;

    /** ApiKey 值（ak- 前缀，全局唯一） */
    private String apiKey;

    /** 状态：1 启用 0 禁用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建人（sys_user.id），管理员可看全部 */
    @TableField(fill = FieldFill.INSERT)
    private Long userId;
}
