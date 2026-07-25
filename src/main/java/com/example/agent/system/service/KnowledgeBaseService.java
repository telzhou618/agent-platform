package com.example.agent.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.agent.system.agent.AgentRegistry;
import com.example.agent.system.entity.KnowledgeBase;
import com.example.agent.system.log.OperationLog;
import com.example.agent.system.mapper.KnowledgeBaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseService extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase> {

    private final AgentRegistry agentRegistry;

    /** 分页查询知识库，关键字匹配名称 / 备注 */
    public Page<KnowledgeBase> pageKnowledgeBases(String keyword, int page, int size) {
        return lambdaQuery()
                .and(StrUtil.isNotBlank(keyword), q -> q
                        .like(KnowledgeBase::getName, keyword).or().like(KnowledgeBase::getRemark, keyword))
                .orderByDesc(KnowledgeBase::getCreateTime)
                .page(new Page<>(page, size));
    }

    /** 保存知识库（新增/编辑）：落库后级联重建引用它的智能体实例 */
    @OperationLog(module = "知识库管理", action = "保存", summary = "#knowledgeBase.name")
    public void saveKnowledgeBase(KnowledgeBase knowledgeBase) {
        if (StrUtil.isBlank(knowledgeBase.getName())) {
            throw new IllegalArgumentException("名称不能为空");
        }
        if (StrUtil.isBlank(knowledgeBase.getType())) {
            throw new IllegalArgumentException("请选择类型");
        }
        saveOrUpdate(knowledgeBase);
        agentRegistry.onKnowledgeChanged(knowledgeBase);
    }

    /** 删除知识库：落库后级联重建引用它的智能体实例（移除该知识库） */
    @OperationLog(module = "知识库管理", action = "删除", summary = "#id")
    public void deleteKnowledgeBase(Long id) {
        removeById(id);
        agentRegistry.onKnowledgeDeleted(id);
    }
}
