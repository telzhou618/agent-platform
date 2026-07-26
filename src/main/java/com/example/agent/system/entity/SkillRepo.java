package com.example.agent.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 技能仓库（HarnessAgent 技能来源） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("skill_repo")
public class SkillRepo extends BaseEntity {

    /** 来源类型：Git 仓库 */
    public static final String TYPE_GIT = "git";
    /** 来源类型：MySQL 数据库 */
    public static final String TYPE_MYSQL = "mysql";
    /** 来源类型：classpath 目录 */
    public static final String TYPE_CLASSPATH = "classpath";

    /** 技能仓库名称 */
    private String name;

    /** 来源类型：git/mysql/classpath，未来可扩展 */
    private String type;

    /** 类型相关配置，JSON 对象 */
    private String config;

    /** 备注 */
    private String remark;

    /** 创建人（sys_user.id），管理员可看全部 */
    @TableField(fill = FieldFill.INSERT)
    private Long userId;
}
