package com.example.agent;

import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.hutool.crypto.digest.BCrypt;
import com.example.agent.system.auth.SaTokenContextForVaadin;
import com.example.agent.system.auth.UserTenantHandler;
import com.example.agent.system.entity.SysUser;
import com.example.agent.system.service.SysUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户模块集成测试（需要本地 MySQL 和 Redis）：
 * 验证登录认证、sa-token 的 Redis/Vaadin 适配装配、租户放行逻辑。
 */
@SpringBootTest
class UserModuleTest {

    @Autowired
    private SysUserService sysUserService;
    @Autowired
    private SaTokenDao saTokenDao;
    @Autowired
    private SaTokenContext saTokenContext;
    @Autowired
    private UserTenantHandler userTenantHandler;

    /** 内置管理员已初始化，密码 BCrypt 校验通过/失败分支正确 */
    @Test
    void authenticate() {
        SysUser admin = sysUserService.authenticate("admin", "admin123");
        assertNotNull(admin, "admin/admin123 应认证成功");
        assertTrue(admin.getIsAdmin() == 1, "admin 应是管理员");
        assertNull(sysUserService.authenticate("admin", "wrong-password"), "错误密码应认证失败");
        assertNull(sysUserService.authenticate("no-such-user", "admin123"), "不存在的用户应认证失败");
    }

    /** sa-token 会话 DAO 应装配为 Redis 实现（重启不丢会话） */
    @Test
    void saTokenDaoIsRedisBacked() {
        assertTrue(saTokenDao.getClass().getSimpleName().contains("Redis"),
                "SaTokenDao 应为 Redis 实现，实际：" + saTokenDao.getClass().getName());
    }

    /** sa-token 上下文应装配为 Vaadin 适配（StpUtil 在 Vaadin 线程可用） */
    @Test
    void saTokenContextIsVaadinAdapted() {
        assertInstanceOf(SaTokenContextForVaadin.class, saTokenContext,
                "SaTokenContext 应为 Vaadin 适配实现");
    }

    /** 无登录上下文时（启动引导/异步线程）租户过滤一律放行 */
    @Test
    void tenantIgnoredWithoutLogin() {
        assertTrue(userTenantHandler.ignoreTable("model_config"), "无登录上下文应放行业务表");
        assertTrue(userTenantHandler.ignoreTable("sys_user"), "sys_user 不参与租户过滤");
        assertTrue(userTenantHandler.ignoreTable("chat_record"), "chat_record 不参与租户过滤");
    }

    /** 个人资料：手机号/邮箱仅校验格式；留空表示清除 */
    @Test
    void updateProfile() {
        SysUser user = new SysUser();
        user.setUsername("profile-test-user");
        user.setPassword(BCrypt.hashpw("old-123"));
        sysUserService.save(user);
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> sysUserService.updateProfile(user.getId(), "12345", null), "非法手机号应拦截");
            assertThrows(IllegalArgumentException.class,
                    () -> sysUserService.updateProfile(user.getId(), null, "not-an-email"), "非法邮箱应拦截");

            sysUserService.updateProfile(user.getId(), "13800138000", "a@b.com");
            SysUser updated = sysUserService.getById(user.getId());
            assertEquals("13800138000", updated.getPhone());
            assertEquals("a@b.com", updated.getEmail());

            sysUserService.updateProfile(user.getId(), "", "");
            updated = sysUserService.getById(user.getId());
            assertNull(updated.getPhone(), "留空应清除手机号");
            assertNull(updated.getEmail(), "留空应清除邮箱");
        } finally {
            sysUserService.removeById(user.getId());
        }
    }

    /** 修改密码：原密码错误应拦截，正确则生效 */
    @Test
    void changePassword() {
        SysUser user = new SysUser();
        user.setUsername("password-test-user");
        user.setPassword(BCrypt.hashpw("old-123"));
        sysUserService.save(user);
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> sysUserService.changePassword(user.getId(), "wrong", "new-456"), "原密码错误应拦截");

            sysUserService.changePassword(user.getId(), "old-123", "new-456");
            assertNotNull(sysUserService.authenticate("password-test-user", "new-456"), "新密码应能登录");
            assertNull(sysUserService.authenticate("password-test-user", "old-123"), "旧密码应失效");
        } finally {
            sysUserService.removeById(user.getId());
        }
    }
}
