package com.example.agent.system.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 登录成功后缓存在 VaadinSession 中的当前用户信息 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    private Long id;

    private String username;

    /** 是否管理员：1 是 0 否 */
    private Integer isAdmin;
}
