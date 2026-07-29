package com.example.agent.proxy;

import cn.hutool.core.util.StrUtil;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 敏感词过滤器：houbb sensitive-word 内置默认词库。
 * 全局共享一个实例：init 时加载词库构建索引，之后只读查询，线程安全。
 * 关闭数字/邮箱/URL/IPv4 检测避免正常对话误伤，只保留词语检测。
 */
@Component
public class SensitiveWordFilter {

    /** 命中敏感词时的错误码（参数类问题） */
    static final int SENSITIVE_HIT_CODE = 400;

    private final SensitiveWordBs sensitiveWordBs = SensitiveWordBs.newInstance()
            .ignoreCase(true)
            .ignoreWidth(true)
            .ignoreRepeat(true)
            .enableNumCheck(false)
            .enableEmailCheck(false)
            .enableUrlCheck(false)
            .enableIpv4Check(false)
            .init();

    /** 命中的敏感词（去重），无命中返回空列表 */
    public List<String> findAll(String text) {
        if (StrUtil.isBlank(text)) {
            return List.of();
        }
        return sensitiveWordBs.findAll(text).stream().distinct().toList();
    }

    /** 校验文本，命中敏感词抛出 {@link AgentProxyException}（400） */
    public void check(String text) {
        List<String> hits = findAll(text);
        if (!hits.isEmpty()) {
            throw new AgentProxyException(SENSITIVE_HIT_CODE,
                    "消息包含敏感词，请修改后重试：" + String.join("、", hits));
        }
    }
}
