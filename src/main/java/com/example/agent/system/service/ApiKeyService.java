package com.example.agent.system.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.agent.system.entity.ApiKey;
import com.example.agent.system.log.OperationLog;
import com.example.agent.system.mapper.ApiKeyMapper;
import org.springframework.stereotype.Service;

/**
 * ApiKey 管理。数据权限由租户拦截器保证：普通用户只能读写自己的 Key，管理员看全部；
 * 状态切换/删除前再做一次显式校验，防止越权请求静默成功。
 */
@Service
public class ApiKeyService extends ServiceImpl<ApiKeyMapper, ApiKey> {

    /** Key 值前缀 */
    private static final String KEY_PREFIX = "ak-";

    /** 分页查询 ApiKey，关键字匹配名称 / 备注 */
    public Page<ApiKey> pageApiKeys(String keyword, int page, int size) {
        return lambdaQuery()
                .and(StrUtil.isNotBlank(keyword), q -> q
                        .like(ApiKey::getName, keyword).or().like(ApiKey::getRemark, keyword))
                .orderByDesc(ApiKey::getCreateTime)
                .page(new Page<>(page, size));
    }

    /** 创建 ApiKey：Key 值由服务端生成，未指定状态时默认启用 */
    @OperationLog(module = "ApiKey管理", action = "保存", summary = "#apiKey.name")
    public void saveApiKey(ApiKey apiKey) {
        if (StrUtil.isBlank(apiKey.getName())) {
            throw new IllegalArgumentException("名称不能为空");
        }
        if (apiKey.getStatus() == null) {
            apiKey.setStatus(1);
        }
        apiKey.setApiKey(KEY_PREFIX + IdUtil.fastSimpleUUID());
        save(apiKey);
    }

    /** 启用/禁用 ApiKey：非本人记录被租户过滤返回 null，显式报错防止静默成功 */
    @OperationLog(module = "ApiKey管理", action = "切换状态", summary = "#id")
    public void updateStatus(Long id, int status) {
        ApiKey existing = getById(id);
        if (existing == null) {
            throw new IllegalStateException("ApiKey 不存在或无权操作");
        }
        existing.setStatus(status);
        updateById(existing);
    }

    /** 删除 ApiKey：非本人记录被租户拦截器挡下（影响 0 行），显式报错防止静默成功 */
    @OperationLog(module = "ApiKey管理", action = "删除", summary = "#id")
    public void deleteApiKey(Long id) {
        if (!removeById(id)) {
            throw new IllegalStateException("ApiKey 不存在或无权删除");
        }
    }
}
