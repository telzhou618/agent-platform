package com.example.agent.system.auth;

import com.example.agent.ui.LoginView;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.stereotype.Component;

/**
 * 路由守卫：未登录访问任何页面一律跳到登录页；
 * 已登录访问登录页则跳回首页。
 */
@Component
public class AuthServiceInitListener implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiEvent ->
                uiEvent.getUI().addBeforeEnterListener(this::beforeEnter));
    }

    private void beforeEnter(BeforeEnterEvent event) {
        boolean toLogin = LoginView.class.equals(event.getNavigationTarget());
        if (!LoginHelper.isLoggedIn() && !toLogin) {
            event.rerouteTo(LoginView.class);
        } else if (LoginHelper.isLoggedIn() && toLogin) {
            event.rerouteTo("");
        }
    }
}
