package com.example.agent.system.auth;

import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.context.model.SaCookie;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.context.model.SaStorage;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * sa-token 的 Vaadin 上下文适配。
 * sa-token 默认的 Spring 上下文依赖 RequestContextHolder，而 Vaadin 请求
 * 不经过 Spring MVC，事件/路由守卫线程里拿不到；这里把 sa-token 的
 * Request/Response/Storage 适配到 Vaadin 的对应对象上，使 StpUtil 在
 * Vaadin 线程内完全可用。@Primary 保证 sa-token 注入本实现而非默认实现。
 */
@Primary
@Component
public class SaTokenContextForVaadin implements SaTokenContext {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    @Override
    public SaRequest getRequest() {
        return new VaadinSaRequest();
    }

    @Override
    public SaResponse getResponse() {
        return new VaadinSaResponse();
    }

    @Override
    public SaStorage getStorage() {
        return new VaadinSaStorage();
    }

    @Override
    public boolean matchPath(String pattern, String path) {
        return PATH_MATCHER.match(pattern, path);
    }

    /** SaRequest -> VaadinRequest */
    static class VaadinSaRequest implements SaRequest {

        private VaadinRequest req() {
            VaadinRequest request = VaadinService.getCurrentRequest();
            if (request == null) {
                throw new IllegalStateException("当前线程无 Vaadin 请求上下文");
            }
            return request;
        }

        @Override
        public Object getSource() {
            return req();
        }

        @Override
        public String getParam(String name) {
            return req().getParameter(name);
        }

        @Override
        public List<String> getParamNames() {
            return new ArrayList<>(req().getParameterMap().keySet());
        }

        @Override
        public Map<String, String> getParamMap() {
            Map<String, String> map = new LinkedHashMap<>();
            req().getParameterMap().forEach((k, v) -> map.put(k, v != null && v.length > 0 ? v[0] : null));
            return map;
        }

        @Override
        public String getHeader(String name) {
            return req().getHeader(name);
        }

        @Override
        public String getCookieValue(String name) {
            return getCookieFirstValue(name);
        }

        @Override
        public String getCookieFirstValue(String name) {
            Cookie[] cookies = req().getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (cookie.getName().equals(name)) {
                        return cookie.getValue();
                    }
                }
            }
            return null;
        }

        @Override
        public String getCookieLastValue(String name) {
            String value = null;
            Cookie[] cookies = req().getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (cookie.getName().equals(name)) {
                        value = cookie.getValue();
                    }
                }
            }
            return value;
        }

        @Override
        public String getRequestPath() {
            String path = req().getPathInfo();
            return path == null ? "/" : path;
        }

        @Override
        public String getUrl() {
            return getRequestPath();
        }

        @Override
        public String getMethod() {
            return req().getMethod();
        }

        @Override
        public Object forward(String path) {
            return null;
        }
    }

    /** SaResponse -> VaadinResponse */
    static class VaadinSaResponse implements SaResponse {

        private VaadinResponse resp() {
            VaadinResponse response = VaadinService.getCurrentResponse();
            if (response == null) {
                throw new IllegalStateException("当前线程无 Vaadin 响应上下文");
            }
            return response;
        }

        @Override
        public Object getSource() {
            return resp();
        }

        @Override
        public SaResponse setStatus(int sc) {
            resp().setStatus(sc);
            return this;
        }

        @Override
        public SaResponse setHeader(String name, String value) {
            resp().setHeader(name, value);
            return this;
        }

        @Override
        public SaResponse addHeader(String name, String value) {
            resp().setHeader(name, value);
            return this;
        }

        @Override
        public void addCookie(SaCookie saCookie) {
            Cookie cookie = new Cookie(saCookie.getName(), saCookie.getValue());
            cookie.setPath(saCookie.getPath() == null ? "/" : saCookie.getPath());
            cookie.setMaxAge(saCookie.getMaxAge());
            if (saCookie.getDomain() != null) {
                cookie.setDomain(saCookie.getDomain());
            }
            cookie.setHttpOnly(saCookie.getHttpOnly() == null || saCookie.getHttpOnly());
            cookie.setSecure(Boolean.TRUE.equals(saCookie.getSecure()));
            resp().addCookie(cookie);
        }

        @Override
        public Object redirect(String url) {
            return null;
        }
    }

    /** SaStorage -> VaadinSession 属性 */
    static class VaadinSaStorage implements SaStorage {

        private VaadinSession session() {
            VaadinSession session = VaadinSession.getCurrent();
            if (session == null) {
                throw new IllegalStateException("当前线程无 Vaadin 会话上下文");
            }
            return session;
        }

        @Override
        public Object getSource() {
            return session();
        }

        @Override
        public Object get(String key) {
            return session().getAttribute(key);
        }

        @Override
        public SaStorage set(String key, Object value) {
            session().setAttribute(key, value);
            return this;
        }

        @Override
        public SaStorage delete(String key) {
            session().setAttribute(key, (Object) null);
            return this;
        }
    }
}
