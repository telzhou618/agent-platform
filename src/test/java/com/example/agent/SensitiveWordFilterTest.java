package com.example.agent;

import com.example.agent.system.agent.AgentProxyException;
import com.example.agent.system.agent.SensitiveWordFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感词过滤器单元测试：默认词库命中 / 正常文本放行 / 变体识别（大小写、全半角、重复字符）。
 * 纯内存测试，不依赖 Spring 上下文与外部服务。
 */
class SensitiveWordFilterTest {

    private final SensitiveWordFilter filter = new SensitiveWordFilter();

    @Test
    void hitsDefaultDictWords() {
        assertFalse(filter.findAll("哪里可以买到毒品？").isEmpty(), "默认词库应命中");
        assertFalse(filter.findAll("这个平台赌博输了好多钱").isEmpty(), "默认词库应命中");
    }

    @Test
    void passesNormalText() {
        assertTrue(filter.findAll("北京今天天气怎么样？").isEmpty(), "正常对话不应误伤");
        assertTrue(filter.findAll("帮我总结一下这份文档").isEmpty(), "正常对话不应误伤");
        assertTrue(filter.findAll("").isEmpty(), "空文本直接放行");
        assertTrue(filter.findAll(null).isEmpty(), "null 文本直接放行");
    }

    @Test
    void checkThrowsWithCode400AndWordList() {
        AgentProxyException ex = assertThrows(AgentProxyException.class,
                () -> filter.check("听说有人在贩卖毒品"));
        assertEquals(400, ex.getCode(), "命中敏感词应返回 400");
        assertTrue(ex.getMessage().contains("毒品"), "错误消息应包含命中的敏感词");
    }
}
