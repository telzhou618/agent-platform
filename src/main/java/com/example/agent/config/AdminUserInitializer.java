package com.example.agent.config;

import cn.hutool.crypto.digest.BCrypt;
import com.example.agent.system.entity.SysUser;
import com.example.agent.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 启动引导：sys_user 为空时创建内置管理员 admin/admin123 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserInitializer implements ApplicationRunner {

    private final SysUserService sysUserService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (sysUserService.count() == 0) {
                SysUser admin = new SysUser();
                admin.setUsername("admin");
                admin.setPassword(BCrypt.hashpw("admin123"));
                admin.setIsAdmin(1);
                sysUserService.save(admin);
                log.info("已创建内置管理员账号 admin/admin123，请登录后及时修改密码");
            }
        } catch (Exception e) {
            log.warn("初始化管理员账号失败：{}", e.getMessage());
        }
    }
}
