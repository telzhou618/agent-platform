package com.example.agent.ui;

import cn.hutool.core.util.StrUtil;
import com.vaadin.flow.data.binder.Validator;

/**
 * 表单格式校验器：统一各弹框的格式校验规则与中文错误提示。
 * 全部允许空值（必填交给 asRequired），仅非空时校验格式。
 */
public final class FormValidators {

    /** URL 规则：http(s):// 开头且无空白字符（知识库 Dify 地址等手动校验场景复用） */
    public static final String URL_PATTERN = "^https?://\\S+$";

    private FormValidators() {
    }

    /** 邮箱：非空时须为合法邮箱格式 */
    public static Validator<String> email() {
        return Validator.from(v -> StrUtil.isBlank(v) || cn.hutool.core.lang.Validator.isEmail(v),
                "邮箱格式不正确");
    }

    /** 手机号：非空时须为 11 位手机号 */
    public static Validator<String> mobile() {
        return Validator.from(v -> StrUtil.isBlank(v) || cn.hutool.core.lang.Validator.isMobile(v),
                "手机号格式不正确");
    }

    /** URL：非空时须以 http:// 或 https:// 开头 */
    public static Validator<String> url() {
        return Validator.from(v -> StrUtil.isBlank(v) || v.matches(URL_PATTERN),
                "URL 应以 http:// 或 https:// 开头");
    }

    /** 密码：非空时至少 min 位（空值放行，配合"留空表示不修改"场景） */
    public static Validator<String> passwordMin(int min) {
        return Validator.from(v -> StrUtil.isBlank(v) || v.length() >= min,
                "密码至少 " + min + " 位");
    }
}
