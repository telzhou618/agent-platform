package com.example.agent.ui.component;

import cn.hutool.core.util.StrUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;

/**
 * 智能体头像渲染：emoji 表情直接渲染文本；http(s) URL 渲染圆形图片；
 * 未配置时取名称首字符作为兜底。
 */
public final class AgentAvatar {

    private AgentAvatar() {
    }

    /** 判断头像值是否为图片 URL */
    public static boolean isImageUrl(String avatar) {
        return StrUtil.isNotBlank(avatar)
                && (avatar.startsWith("http://") || avatar.startsWith("https://"));
    }

    /**
     * 构建头像组件
     *
     * @param avatar 配置的头像（emoji 或图片 URL），可为空
     * @param name   智能体名称（兜底取首字符），可为空
     * @param sizePx 直径（px）
     */
    public static Component create(String avatar, String name, int sizePx) {
        if (isImageUrl(avatar)) {
            Image image = new Image(avatar, "头像");
            image.setWidth(sizePx + "px");
            image.setHeight(sizePx + "px");
            image.getStyle()
                    .set("border-radius", "50%")
                    .set("object-fit", "cover")
                    .set("flex-shrink", "0");
            return image;
        }
        Span span = new Span(StrUtil.isNotBlank(avatar) ? avatar : firstChar(name));
        span.getStyle()
                .set("width", sizePx + "px")
                .set("height", sizePx + "px")
                .set("min-width", sizePx + "px")
                .set("border-radius", "50%")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("display", "inline-flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("font-size", Math.max(10, sizePx * 3 / 5) + "px")
                .set("line-height", "1");
        return span;
    }

    private static String firstChar(String name) {
        if (StrUtil.isBlank(name)) {
            return "?";
        }
        return String.valueOf(Character.toUpperCase(name.trim().charAt(0)));
    }
}
