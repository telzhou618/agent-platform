package com.example.agent.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.agent.system.agent.AgentRegistry;
import com.example.agent.system.agent.ModelAvailabilityChecker;
import com.example.agent.system.entity.ModelConfig;
import com.example.agent.system.log.OperationLog;
import com.example.agent.system.mapper.ModelConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ModelConfigService extends ServiceImpl<ModelConfigMapper, ModelConfig> {

    private final AgentRegistry agentRegistry;
    private final ModelAvailabilityChecker availabilityChecker;

    /** 分页查询模型，关键字匹配名称 / 模型标识 */
    public Page<ModelConfig> pageModels(String keyword, int page, int size) {
        return lambdaQuery()
                .and(StrUtil.isNotBlank(keyword), q -> q
                        .like(ModelConfig::getName, keyword).or().like(ModelConfig::getModel, keyword))
                .orderByDesc(ModelConfig::getCreateTime)
                .page(new Page<>(page, size));
    }

    /**
     * 保存模型：自定义供应商必须填写 API 地址；保存前真实调用验证可用性，不可用则拒绝保存；
     * 落库后级联重建引用它的智能体实例
     */
    @OperationLog(module = "模型管理", action = "保存", summary = "#model.name")
    public void saveModel(ModelConfig model) {
        if ("custom".equals(model.getProvider()) && StrUtil.isBlank(model.getBaseUrl())) {
            throw new IllegalArgumentException("自定义供应商必须填写 API 地址");
        }
        availabilityChecker.check(model);
        model.setAvailable(1);
        model.setCheckMsg(null);
        saveOrUpdate(model);
        agentRegistry.onModelChanged(model);
    }

    /** 重新检测已有模型的可用性：只更新状态与原因，无论结果如何都保留记录 */
    @OperationLog(module = "模型管理", action = "检测", summary = "#id")
    public void recheckModel(Long id) {
        ModelConfig model = getById(id);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在");
        }
        try {
            availabilityChecker.check(model);
            model.setAvailable(1);
            model.setCheckMsg(null);
        } catch (Exception e) {
            model.setAvailable(0);
            model.setCheckMsg(StrUtil.maxLength(e.getMessage(), 500));
        }
        updateById(model);
        if (model.getAvailable() != 1) {
            throw new IllegalArgumentException(model.getCheckMsg());
        }
    }

    /**
     * 模型可用状态统计：[可用数, 不可用数]（available 非 1 的一律按不可用计，含历史未检测数据）。
     * 数据权限由租户插件自动过滤：普通用户只统计自己创建的模型。
     */
    public long[] availabilitySummary() {
        long available = lambdaQuery().eq(ModelConfig::getAvailable, 1).count();
        return new long[]{available, count() - available};
    }

    /** 删除模型：落库后级联重建引用它的智能体实例（回退默认模型） */
    @OperationLog(module = "模型管理", action = "删除", summary = "#id")
    public void deleteModel(Long id) {
        removeById(id);
        agentRegistry.onModelDeleted(id);
    }
}
