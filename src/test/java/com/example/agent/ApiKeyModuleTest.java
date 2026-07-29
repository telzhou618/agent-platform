package com.example.agent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.system.entity.ApiKey;
import com.example.agent.system.service.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ApiKey 模块集成测试（需要本地 MySQL）：
 * 验证 Key 自动生成、关键字分页过滤、越权/不存在记录的显式报错分支。
 * 测试无登录上下文，租户拦截器放行（视同管理员语义）。
 */
@SpringBootTest
class ApiKeyModuleTest {

    @Autowired
    private ApiKeyService apiKeyService;

    /** 新增时服务端自动生成 ak- 前缀 Key 并落库 */
    @Test
    void createAutoGeneratesKey() {
        ApiKey key = new ApiKey();
        key.setName("单元测试Key");
        key.setRemark("ApiKeyModuleTest");
        apiKeyService.saveApiKey(key);

        assertNotNull(key.getId(), "保存后应回填 ID");
        assertNotNull(key.getApiKey(), "应自动生成 Key 值");
        assertTrue(key.getApiKey().startsWith("ak-"), "Key 应以 ak- 开头：" + key.getApiKey());
        assertEquals(35, key.getApiKey().length(), "Key 应为 ak- + 32 位随机串");
        assertEquals(1, key.getStatus(), "未指定状态时默认启用");

        ApiKey fromDb = apiKeyService.getById(key.getId());
        assertNotNull(fromDb, "落库后应能按 ID 查到");
        assertEquals(key.getApiKey(), fromDb.getApiKey());

        apiKeyService.deleteApiKey(key.getId());
    }

    /** 分页查询按名称 / 备注关键字过滤 */
    @Test
    void pageFiltersByKeyword() {
        ApiKey key = new ApiKey();
        key.setName("关键字过滤专用名XYZ");
        apiKeyService.saveApiKey(key);

        Page<ApiKey> hit = apiKeyService.pageApiKeys("关键字过滤专用名XYZ", 1, 10);
        assertTrue(hit.getRecords().stream().anyMatch(k -> k.getId().equals(key.getId())),
                "按名称关键字应能查到");

        Page<ApiKey> miss = apiKeyService.pageApiKeys("绝不可能存在的关键字ABC123", 1, 10);
        assertEquals(0, miss.getTotal(), "无关关键字应查不到记录");

        apiKeyService.deleteApiKey(key.getId());
    }

    /** 删除不存在（或无权限）的记录显式报错，而不是静默成功 */
    @Test
    void deleteNonExistentThrows() {
        assertThrows(IllegalStateException.class, () -> apiKeyService.deleteApiKey(-999L));
    }

    /** 编辑不存在（或无权限）的记录显式报错 */
    @Test
    void editNonExistentThrows() {
        ApiKey key = new ApiKey();
        key.setId(-999L);
        key.setName("不存在的记录");
        assertThrows(IllegalStateException.class, () -> apiKeyService.saveApiKey(key));
    }
}
