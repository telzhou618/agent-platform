package com.example.agent.system.service;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.agent.system.auth.LoginHelper;
import com.example.agent.system.entity.SysUser;
import com.example.agent.system.log.OperationLog;
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
    @OperationLog(module = "用户管理", action = "保存", summary = "#user.username")
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

    /** 删除用户（仅管理员可执行）；管理员账号不可删除 */
    @OperationLog(module = "用户管理", action = "删除", summary = "#id")
    public void deleteUser(Long id) {
        checkAdmin();
        SysUser user = getById(id);
        if (user == null) {
            return;
        }
        if (Integer.valueOf(1).equals(user.getIsAdmin())) {
            throw new IllegalArgumentException("管理员账号不可删除");
        }
        removeById(id);
    }

    /** 登录认证：成功返回用户，失败返回 null（logParams=false：参数含明文密码，不落日志） */
    @OperationLog(module = "用户登录", action = "登录", summary = "#username", successByResult = true, logParams = false)
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

    /** 修改个人资料：手机号/邮箱仅校验格式不校验真实性，留空表示清除 */
    @OperationLog(module = "个人中心", action = "修改资料", summary = "#userId")
    public void updateProfile(Long userId, String phone, String email) {
        if (StrUtil.isNotBlank(phone) && !Validator.isMobile(phone)) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        if (StrUtil.isNotBlank(email) && !Validator.isEmail(email)) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
        SysUser user = getById(userId);
        if (user == null) {
            throw new IllegalStateException("用户不存在");
        }
        // updateById 会忽略 null 字段，留空清除需走 UpdateWrapper 显式 set
        lambdaUpdate().eq(SysUser::getId, userId)
                .set(SysUser::getPhone, StrUtil.blankToDefault(phone, null))
                .set(SysUser::getEmail, StrUtil.blankToDefault(email, null))
                .update();
    }

    /** 修改头像：仅接受 http(s) 图片 URL，留空恢复默认（用户名首字） */
    @OperationLog(module = "个人中心", action = "修改头像", summary = "#userId")
    public void updateAvatar(Long userId, String avatar) {
        if (StrUtil.isNotBlank(avatar)
                && !avatar.startsWith("http://") && !avatar.startsWith("https://")) {
            throw new IllegalArgumentException("头像需为 http(s) 图片 URL");
        }
        SysUser user = getById(userId);
        if (user == null) {
            throw new IllegalStateException("用户不存在");
        }
        lambdaUpdate().eq(SysUser::getId, userId)
                .set(SysUser::getAvatar, StrUtil.blankToDefault(avatar, null))
                .update();
    }

    /** 修改密码：必须验证原密码（logParams=false：参数含明文密码，不落日志） */
    @OperationLog(module = "个人中心", action = "修改密码", summary = "#userId", logParams = false)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        if (StrUtil.isBlank(newPassword)) {
            throw new IllegalArgumentException("新密码不能为空");
        }
        SysUser user = getById(userId);
        if (user == null) {
            throw new IllegalStateException("用户不存在");
        }
        if (StrUtil.isBlank(oldPassword) || !BCrypt.checkpw(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("原密码不正确");
        }
        user.setPassword(BCrypt.hashpw(newPassword));
        updateById(user);
    }

    private void checkAdmin() {
        if (!LoginHelper.isAdmin()) {
            throw new SecurityException("仅管理员可操作用户数据");
        }
    }
}
