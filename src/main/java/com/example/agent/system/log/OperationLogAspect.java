package com.example.agent.system.log;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.agent.system.auth.LoginHelper;
import com.example.agent.system.auth.LoginUser;
import com.example.agent.system.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 操作日志切面：拦截 {@link OperationLog} 注解的方法，成功/失败均落库；
 * 日志写库失败只告警，绝不影响业务调用。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private static final SpelExpressionParser SPEL_PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer PARAM_NAMES = new DefaultParameterNameDiscoverer();
    private static final Map<String, Expression> SPEL_CACHE = new ConcurrentHashMap<>();
    /** 摘要 / 失败原因的最大落库长度 */
    private static final int MAX_TEXT_LENGTH = 500;
    /** 参数 JSON 的最大落库长度 */
    private static final int MAX_PARAMS_LENGTH = 4000;
    /** 参数 JSON 中需要脱敏的字段名（小写） */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "apikey", "api_key", "secret", "token");

    private final OperationLogService operationLogService;

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint pjp, OperationLog opLog) throws Throwable {
        Long userId = LoginHelper.currentUserId();
        LoginUser currentUser = LoginHelper.currentUser();
        String username = currentUser == null ? null : currentUser.getUsername();
        try {
            Object result = pjp.proceed();
            boolean success = !opLog.successByResult() || result != null;
            record(pjp, opLog, userId, username, success, success ? null : "操作返回空");
            return result;
        } catch (Throwable e) {
            record(pjp, opLog, userId, username, false, e.getMessage());
            throw e;
        }
    }

    /** 组装并落库一条日志，任何异常都吞掉只告警 */
    private void record(ProceedingJoinPoint pjp, OperationLog opLog,
                        Long userId, String username, boolean success, String errorMsg) {
        try {
            com.example.agent.system.entity.OperationLog entry =
                    new com.example.agent.system.entity.OperationLog();
            entry.setUserId(userId);
            entry.setUsername(username);
            entry.setModule(opLog.module());
            entry.setAction(opLog.action());
            entry.setSummary(truncate(evalSummary(pjp, opLog.summary())));
            entry.setParams(opLog.logParams() ? truncate(paramsJson(pjp.getArgs())) : null);
            entry.setSuccess(success ? 1 : 0);
            entry.setErrorMsg(truncate(errorMsg));
            entry.setCreateTime(LocalDateTime.now());
            operationLogService.save(entry);
        } catch (Exception e) {
            log.warn("操作日志落库失败：{} {}", opLog.module(), opLog.action(), e);
        }
    }

    /** SpEL 求值摘要：按方法参数名绑定变量（如 #model.name），求值失败降级为 null */
    private String evalSummary(ProceedingJoinPoint pjp, String spel) {
        if (spel == null || spel.isBlank()) {
            return null;
        }
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            String[] names = PARAM_NAMES.getParameterNames(signature.getMethod());
            Object[] args = pjp.getArgs();
            if (names != null) {
                for (int i = 0; i < names.length; i++) {
                    context.setVariable(names[i], args[i]);
                }
            }
            Object value = SPEL_CACHE.computeIfAbsent(spel, SPEL_PARSER::parseExpression).getValue(context);
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH);
    }

    /** 方法参数 -> JSON 数组字符串，敏感字段值替换为 ******；序列化失败降级为 null */
    private static String paramsJson(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            Object json = JSON.parse(JSON.toJSONString(args));
            maskSensitive(json);
            String text = json.toString();
            return text.length() <= MAX_PARAMS_LENGTH ? text : text.substring(0, MAX_PARAMS_LENGTH);
        } catch (Exception e) {
            return null;
        }
    }

    /** 递归脱敏：JSONObject 中命中敏感字段名的值替换为 ****** */
    private static void maskSensitive(Object node) {
        if (node instanceof JSONObject obj) {
            for (String key : new ArrayList<>(obj.keySet())) {
                if (SENSITIVE_KEYS.contains(key.toLowerCase())) {
                    obj.put(key, "******");
                } else {
                    maskSensitive(obj.get(key));
                }
            }
        } else if (node instanceof JSONArray arr) {
            arr.forEach(OperationLogAspect::maskSensitive);
        }
    }
}
