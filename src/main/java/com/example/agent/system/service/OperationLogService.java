package com.example.agent.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.agent.system.entity.OperationLog;
import com.example.agent.system.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;

/** 操作日志：AOP 落库 + 管理员分页查看 */
@Service
public class OperationLogService extends ServiceImpl<OperationLogMapper, OperationLog> {

    /** 分页查询日志，关键字匹配操作人 / 模块 / 摘要，按时间倒序 */
    public Page<OperationLog> pageLogs(String keyword, int page, int size) {
        return lambdaQuery()
                .and(StrUtil.isNotBlank(keyword), q -> q
                        .like(OperationLog::getUsername, keyword).or()
                        .like(OperationLog::getModule, keyword).or()
                        .like(OperationLog::getSummary, keyword))
                .orderByDesc(OperationLog::getCreateTime)
                .page(new Page<>(page, size));
    }
}
