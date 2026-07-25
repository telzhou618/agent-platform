package com.example.agent.system.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.example.agent.system.entity.SysUser;
import com.example.agent.system.service.SysUserService;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

/**
 * 登录态工具。所有方法在无登录 / 无 Vaadin 上下文（异步线程）时安全返回兜底值，
 * 调用方无需关心线程环境。
 */
@Component
public class LoginHelper {

    private static SysUserService sysUserService;

    public LoginHelper(SysUserService sysUserService) {
        LoginHelper.sysUserService = sysUserService;
    }

    /** 当前登录用户 ID；未登录或非 Web 线程返回 null */
    public static Long currentUserId() {
        LoginUser user = currentUser();
        if (user != null) {
            return user.getId();
        }
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 当前登录用户；未登录返回 null。
     * 优先读 VaadinSession 缓存；应用重启后缓存丢失但 sa-token 会话仍在（Redis），
     * 此时按登录 ID 从库中加载并重新缓存。
     */
    public static LoginUser currentUser() {
        VaadinSession session;
        try {
            session = VaadinSession.getCurrent();
        } catch (Exception e) {
            return null;
        }
        if (session == null) {
            return null;
        }
        LoginUser cached = session.getAttribute(LoginUser.class);
        if (cached != null) {
            return cached;
        }
        try {
            if (!StpUtil.isLogin()) {
                return null;
            }
            SysUser user = sysUserService.getById(StpUtil.getLoginIdAsLong());
            if (user == null) {
                return null;
            }
            LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getIsAdmin());
            session.setAttribute(LoginUser.class, loginUser);
            return loginUser;
        } catch (Exception e) {
            return null;
        }
    }

    /** 是否已登录 */
    public static boolean isLoggedIn() {
        return currentUserId() != null;
    }

    /** 是否管理员（未登录返回 false） */
    public static boolean isAdmin() {
        LoginUser user = currentUser();
        return user != null && Integer.valueOf(1).equals(user.getIsAdmin());
    }

    /** 登录：建立 sa-token 会话（持久化到 Redis），并把用户信息缓存在 VaadinSession */
    public static void login(SysUser user) {
        StpUtil.login(user.getId());
        VaadinSession.getCurrent().setAttribute(LoginUser.class,
                new LoginUser(user.getId(), user.getUsername(), user.getIsAdmin()));
    }

    /** 登出：注销 sa-token 会话并清空缓存 */
    public static void logout() {
        try {
            StpUtil.logout();
        } catch (Exception ignored) {
        }
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute(LoginUser.class, null);
        }
    }
}
