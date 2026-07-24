package com.example.agent.ui;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

/**
 * 统一的消息提示工具：成功绿色、失败红色，顶部居中弹出。
 */
public final class Notify {

    private static final int SUCCESS_DURATION = 2500;
    private static final int ERROR_DURATION = 4000;

    private Notify() {
    }

    /** 成功提示（绿色）。 */
    public static void success(String message) {
        show(message, SUCCESS_DURATION, NotificationVariant.LUMO_SUCCESS);
    }

    /** 失败提示（红色）。 */
    public static void error(String message) {
        show(message, ERROR_DURATION, NotificationVariant.LUMO_ERROR);
    }

    private static void show(String message, int duration, NotificationVariant variant) {
        Notification notification = new Notification(message, duration, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(variant);
        notification.open();
    }
}
