package com.example.agent.system.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.agent.system.auth.LoginHelper;
import com.example.agent.system.entity.SysUser;
import com.example.agent.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SysUserService extends ServiceImpl<SysUserMapper, SysUser> {

    /** 分页查询用户，关键字匹配用户名 / 手机号 / 邮箱（仅管理员） */
    public Page<SysUser> pageUsers(String keyword, int page, int size) {
        checkAdmin();
        return lambdaQuery()
                .and(StrUtil.isNotBlank(keyword), q -> q
                        .like(SysUser::getUsername, keyword).or()
                        .like(SysUser::getPhone, keyword).or()
                        .like(SysUser::getEmail, keyword))
                .orderByDesc(SysUser::getCreateTime)
                .page(new Page<>(page, size));
    }

    /** 保存用户：用户名查重；新增必须设置密码，编辑时密码留空表示不修改（仅管理员） */
    public void saveUser(SysUser user) {
        checkAdmin();
        SysUser exist = lambdaQuery().eq(SysUser::getUsername, user.getUsername()).one();
        if (exist != null && !exist.getId().equals(user.getId())) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (user.getId() == null) {
            if (StrUtil.isBlank(user.getPassword())) {
                throw new IllegalArgumentException("新用户必须设置密码");
            }
            user.setPassword(BCrypt.hashpw(user.getPassword()));
        } else if (StrUtil.isNotBlank(user.getPassword())) {
            user.setPassword(BCrypt.hashpw(user.getPassword()));
        } else {
            // 密码留空：置 null，MyBatis-Plus 更新时忽略该字段
            user.setPassword(null);
        }
        if (user.getIsAdmin() == null) {
            user.setIsAdmin(0);
        }
        saveOrUpdate(user);
    }

    /** 删除用户（仅管理员），内置管理员 admin 不可删除 */
    public void deleteUser(Long id) {
        checkAdmin();
        if (id != null && id == 1L) {
            throw new IllegalArgumentException("内置管理员不可删除");
        }
        removeById(id);
    }

    /** 登录认证：成功返回用户，失败返回 null */
    public SysUser authenticate(String username, String rawPassword) {
        if (StrUtil.isBlank(username) || StrUtil.isBlank(rawPassword)) {
            return null;
        }
        SysUser user = lambdaQuery().eq(SysUser::getUsername, username).one();
        if (user == null || !BCrypt.checkpw(rawPassword, user.getPassword())) {
            return null;
        }
        return user;
    }

    private void checkAdmin() {
        if (!LoginHelper.isAdmin()) {
            throw new SecurityException("仅管理员可操作用户数据");
        }
    }
}
