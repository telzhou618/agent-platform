package com.example.agent.system.auth;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 用户级数据权限：四张业务表自动按 user_id 过滤，
 * 普通用户只能读写自己创建的数据；管理员与无登录上下文（启动引导 / 异步线程）放行。
 */
@Component
public class UserTenantHandler implements TenantLineHandler {

    /** 参与用户级数据权限的表 */
    private static final Set<String> TENANT_TABLES = Set.of(
            "model_config", "agent_info", "mcp_server", "knowledge_base", "api_key");

    @Override
    public Expression getTenantId() {
        Long userId = LoginHelper.currentUserId();
        return new LongValue(userId == null ? -1 : userId);
    }

    @Override
    public String getTenantIdColumn() {
        return "user_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return !TENANT_TABLES.contains(tableName)
                || LoginHelper.currentUserId() == null
                || LoginHelper.isAdmin();
    }
}
