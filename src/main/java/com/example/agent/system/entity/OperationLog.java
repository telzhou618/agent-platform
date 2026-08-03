package com.example.agent.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 操作日志（AOP 记录，仅管理员查看） */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人（sys_user.id），未登录为 null */
    private Long userId;

    /** 操作人账号（冗余，防用户删除后丢失） */
    private String username;

    /** 模块，如 模型管理 */
    private String module;

    /** 操作，如 保存/删除/登录 */
    private String action;

    /** 操作摘要，如 模型名称 */
    private String summary;

    /** 完整方法参数（JSON 数组，敏感字段脱敏；logParams=false 时为 null） */
    private String params;

    /** 是否成功：1 成功 0 失败 */
    private Integer success;

    /** 失败原因 */
    private String errorMsg;

    /** 操作时间 */
    private LocalDateTime createTime;
}
