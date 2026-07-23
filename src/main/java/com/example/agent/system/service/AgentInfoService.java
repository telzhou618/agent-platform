package com.example.agent.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.agent.system.entity.AgentInfo;
import com.example.agent.system.mapper.AgentInfoMapper;
import org.springframework.stereotype.Service;

@Service
public class AgentInfoService extends ServiceImpl<AgentInfoMapper, AgentInfo> {

    /** 分页查询智能体，关键字匹配名称 / 描述 */
    public Page<AgentInfo> pageAgents(String keyword, int page, int size) {
        return lambdaQuery()
                .and(StrUtil.isNotBlank(keyword), q -> q
                        .like(AgentInfo::getName, keyword).or().like(AgentInfo::getDescription, keyword))
                .orderByDesc(AgentInfo::getCreateTime)
                .page(new Page<>(page, size));
    }

    /** 保存智能体：必须选择模型 */
    public void saveAgent(AgentInfo agent) {
        if (agent.getModelId() == null) {
            throw new IllegalArgumentException("请选择模型");
        }
        saveOrUpdate(agent);
    }
}
