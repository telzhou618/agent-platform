package com.example.agent.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.agent.system.agent.AgentRegistry;
import com.example.agent.system.entity.ModelConfig;
import com.example.agent.system.mapper.ModelConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ModelConfigService extends ServiceImpl<ModelConfigMapper, ModelConfig> {

    private final AgentRegistry agentRegistry;

    /** 分页查询模型，关键字匹配名称 / 模型标识 */
    public Page<ModelConfig> pageModels(String keyword, int page, int size) {
        return lambdaQuery()
                .and(StrUtil.isNotBlank(keyword), q -> q
                        .like(ModelConfig::getName, keyword).or().like(ModelConfig::getModel, keyword))
                .orderByDesc(ModelConfig::getCreateTime)
                .page(new Page<>(page, size));
    }

    /** 保存模型：自定义供应商必须填写 API 地址；落库后级联重建引用它的智能体实例 */
    public void saveModel(ModelConfig model) {
        if ("custom".equals(model.getProvider()) && StrUtil.isBlank(model.getBaseUrl())) {
            throw new IllegalArgumentException("自定义供应商必须填写 API 地址");
        }
        saveOrUpdate(model);
        agentRegistry.onModelChanged(model);
    }

    /** 删除模型：落库后级联重建引用它的智能体实例（回退默认模型） */
    public void deleteModel(Long id) {
        removeById(id);
        agentRegistry.onModelDeleted(id);
    }
}
