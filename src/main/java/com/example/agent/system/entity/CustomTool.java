package com.example.agent.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 自定义工具（HTTP 远程接口代理） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("custom_tool")
public class CustomTool extends BaseEntity {

    /** 工具标识（唯一，小写字母/数字/下划线，模型调用名） */
    private String toolKey;

    /** 工具名称（展示用） */
    private String name;

    /** 工具描述（模型据此决定何时调用） */
    private String description;

    /** 接口地址，支持 {参数名} 路径占位符 */
    private String url;

    /** 请求方式：GET/POST/PUT/DELETE */
    private String method;

    /** 请求体类型（POST/PUT 时生效）：json/form */
    private String requestType;

    /** 请求头，JSON 对象 {key:value} */
    private String headers;

    /** 参数定义，JSON 数组 [{name,type,description,required}]，type: string/number/boolean */
    private String params;

    /** 创建人（sys_user.id），管理员可看全部 */
    @TableField(fill = FieldFill.INSERT)
    private Long userId;
}
