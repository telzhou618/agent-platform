package com.example.agent.system.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.agent.system.entity.SkillRepo;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.skill.repository.GitSkillRepository;
import io.agentscope.core.skill.repository.mysql.MysqlSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 按技能仓库配置（skill_repo 表）构建 AgentScope AgentSkillRepository。
 * config 列为类型相关的 JSON 对象，按 type 分支构建；缺少必填配置时抛异常，
 * 由调用方（AgentRegistry）按"单个失败只记日志跳过"策略处理。
 */
@Component
@RequiredArgsConstructor
public class SkillRepoFactory {

    /** mysql 类型直接使用平台自己的数据源 */
    private final DataSource dataSource;

    /** 按类型构建技能仓库实例；类型未知或必填配置缺失时抛异常 */
    public AgentSkillRepository fromConfig(SkillRepo repo) throws Exception {
        JSONObject config = StrUtil.isBlank(repo.getConfig())
                ? new JSONObject() : JSONUtil.parseObj(repo.getConfig());
        return switch (StrUtil.nullToEmpty(repo.getType())) {
            case SkillRepo.TYPE_GIT -> buildGit(config);
            case SkillRepo.TYPE_MYSQL -> buildMysql(config);
            case SkillRepo.TYPE_CLASSPATH -> buildClasspath(config);
            default -> throw new IllegalArgumentException("未知技能仓库类型 " + repo.getType());
        };
    }

    /** Git：url 必填；autoSync 选填（默认 true，HEAD 变化才 pull） */
    private AgentSkillRepository buildGit(JSONObject config) {
        String url = required(config, "url");
        boolean autoSync = config.getBool("autoSync", true);
        return new GitSkillRepository(url, autoSync);
    }

    /** MySQL：databaseName 必填；skillsTableName/writeable 选填；用平台数据源 */
    private AgentSkillRepository buildMysql(JSONObject config) {
        return MysqlSkillRepository.builder(dataSource)
                .databaseName(required(config, "databaseName"))
                .skillsTableName(config.getStr("skillsTableName", "skills"))
                .createIfNotExist(true)
                .writeable(config.getBool("writeable", false))
                .build();
    }

    /** Classpath：directory 必填（如 skills，对应 src/main/resources/skills/） */
    private AgentSkillRepository buildClasspath(JSONObject config) throws Exception {
        return new ClasspathSkillRepository(required(config, "directory"));
    }

    /** 取必填配置项，缺失抛异常（由调用方记日志跳过该仓库） */
    private String required(JSONObject config, String key) {
        String value = config.getStr(key);
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("技能仓库配置缺少必填项 " + key);
        }
        return value;
    }
}
