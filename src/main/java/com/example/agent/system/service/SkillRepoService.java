package com.example.agent.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.agent.system.agent.AgentRegistry;
import com.example.agent.system.agent.SkillRepoFactory;
import com.example.agent.system.entity.SkillRepo;
import com.example.agent.system.log.OperationLog;
import com.example.agent.system.mapper.SkillRepoMapper;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SkillRepoService extends ServiceImpl<SkillRepoMapper, SkillRepo> {

    private final AgentRegistry agentRegistry;
    private final SkillRepoFactory skillRepoFactory;

    /** 分页查询技能仓库，关键字匹配名称 / 备注 */
    public Page<SkillRepo> pageSkillRepos(String keyword, int page, int size) {
        return lambdaQuery()
                .and(StrUtil.isNotBlank(keyword), q -> q
                        .like(SkillRepo::getName, keyword).or().like(SkillRepo::getRemark, keyword))
                .orderByDesc(SkillRepo::getCreateTime)
                .page(new Page<>(page, size));
    }

    /** 保存技能仓库（新增/编辑）：落库后级联重建引用它的智能体实例 */
    @OperationLog(module = "技能仓库管理", action = "保存", summary = "#skillRepo.name")
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
    @OperationLog(module = "技能仓库管理", action = "删除", summary = "#id")
    public void deleteSkillRepo(Long id) {
        removeById(id);
        agentRegistry.onSkillRepoDeleted(id);
    }

    // ---------- 仓库内技能管理（走 AgentScope 自带 AgentSkillRepository API） ----------

    /** 列出仓库内全部技能，三种类型通用（git/classpath 只读场景也只用到这个） */
    public List<AgentSkill> listSkills(Long repoId) {
        try (AgentSkillRepository repository = skillRepoFactory.fromConfig(requireRepo(repoId))) {
            return repository.getAllSkills();
        } catch (Exception e) {
            throw new IllegalStateException("读取技能列表失败：" + e.getMessage(), e);
        }
    }

    /** 保存技能（新增/编辑，同名覆盖）：仅 mysql 类型支持；落库后级联重建引用该仓库的智能体 */
    @OperationLog(module = "技能仓库管理", action = "保存技能", summary = "#skill.name")
    public void saveSkill(Long repoId, AgentSkill skill) {
        SkillRepo repo = requireMysqlRepo(repoId);
        boolean ok;
        try (AgentSkillRepository repository = skillRepoFactory.fromConfig(repo)) {
            ok = repository.save(List.of(skill), true);
        } catch (Exception e) {
            throw new IllegalStateException("保存技能失败：" + e.getMessage(), e);
        }
        if (!ok) {
            throw new IllegalStateException("保存技能失败：该仓库为只读，请在仓库配置中开启「可写」");
        }
        agentRegistry.onSkillRepoChanged(repo);
    }

    /** 删除技能：仅 mysql 类型支持；落库后级联重建引用该仓库的智能体 */
    @OperationLog(module = "技能仓库管理", action = "删除技能", summary = "#skillName")
    public void deleteSkill(Long repoId, String skillName) {
        SkillRepo repo = requireMysqlRepo(repoId);
        boolean ok;
        try (AgentSkillRepository repository = skillRepoFactory.fromConfig(repo)) {
            ok = repository.delete(skillName);
        } catch (Exception e) {
            throw new IllegalStateException("删除技能失败：" + e.getMessage(), e);
        }
        if (!ok) {
            throw new IllegalStateException("删除技能失败：仓库为只读或技能不存在");
        }
        agentRegistry.onSkillRepoChanged(repo);
    }

    /** 按 ID 取仓库（租户插件自动按当前用户过滤，越权访问视为不存在） */
    private SkillRepo requireRepo(Long repoId) {
        SkillRepo repo = getById(repoId);
        if (repo == null) {
            throw new IllegalArgumentException("技能仓库不存在");
        }
        return repo;
    }

    /** 取 mysql 类型仓库，其它类型不支持写操作 */
    private SkillRepo requireMysqlRepo(Long repoId) {
        SkillRepo repo = requireRepo(repoId);
        if (!SkillRepo.TYPE_MYSQL.equals(repo.getType())) {
            throw new IllegalArgumentException("只有 MySQL 类型的技能仓库支持编辑技能");
        }
        return repo;
    }
}
