package com.example.agent.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.agent.system.agent.AgentRegistry;
import com.example.agent.system.entity.SkillRepo;
import com.example.agent.system.log.OperationLog;
import com.example.agent.system.mapper.SkillRepoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SkillRepoService extends ServiceImpl<SkillRepoMapper, SkillRepo> {

    private final AgentRegistry agentRegistry;

    /** 分页查询技能仓库，关键字匹配名称 / 备注 */
    public Page<SkillRepo> pageSkillRepos(String keyword, int page, int size) {
        return lambdaQuery()
                .and(StrUtil.isNotBlank(keyword), q -> q
                        .like(SkillRepo::getName, keyword).or().like(SkillRepo::getRemark, keyword))
                .orderByDesc(SkillRepo::getCreateTime)
                .page(new Page<>(page, size));
    }

    /** 保存技能仓库（新增/编辑）：落库后级联重建引用它的智能体实例 */
    @OperationLog(module = "技能管理", action = "保存", summary = "#skillRepo.name")
    public void saveSkillRepo(SkillRepo skillRepo) {
        if (StrUtil.isBlank(skillRepo.getName())) {
            throw new IllegalArgumentException("名称不能为空");
        }
        if (StrUtil.isBlank(skillRepo.getType())) {
            throw new IllegalArgumentException("请选择类型");
        }
        saveOrUpdate(skillRepo);
        agentRegistry.onSkillRepoChanged(skillRepo);
    }

    /** 删除技能仓库：落库后级联重建引用它的智能体实例（移除该来源） */
    @OperationLog(module = "技能管理", action = "删除", summary = "#id")
    public void deleteSkillRepo(Long id) {
        removeById(id);
        agentRegistry.onSkillRepoDeleted(id);
    }
}
