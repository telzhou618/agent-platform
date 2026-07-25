package com.example.agent.system.log;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注在 Service 方法上，由 {@link OperationLogAspect} 拦截并记录操作日志。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 模块名，如 模型管理 */
    String module();

    /** 操作名，如 保存/删除/登录 */
    String action();

    /** 摘要 SpEL 表达式，按方法参数名引用，如 #model.name；留空不记摘要 */
    String summary() default "";

    /** true 时方法返回 null 记为失败（用于 authenticate 等以 null 表示失败的场景） */
    boolean successByResult() default false;
}
