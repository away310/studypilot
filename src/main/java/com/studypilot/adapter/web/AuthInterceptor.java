package com.studypilot.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录校验拦截器：除白名单外，所有请求都必须已登录。
 *
 * - 页面请求未登录 → 302 跳登录页
 * - 接口请求未登录 → 401 JSON（前端据此跳登录页）
 */
public class AuthInterceptor implements HandlerInterceptor {

    /** 无需登录即可访问的路径（登录页与静态资源）。 */
    private static final String[] WHITELIST = {
            "/login.html",
            "/app.css",
            "/login.css",
            "/favicon.ico",
            "/api/auth/login",
            "/api/auth/status"
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        for (String allowed : WHITELIST) {
            if (path.equals(allowed)) {
                return true;
            }
        }

        HttpSession session = request.getSession(false);
        boolean logged = session != null && session.getAttribute(AuthController.SESSION_KEY) != null;
        if (logged) {
            return true;
        }

        if (path.startsWith("/api/") || expectsJson(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"未登录或登录已过期\",\"authenticated\":false}");
            return false;
        }

        response.sendRedirect(contextPath + "/login.html");
        return false;
    }

    private boolean expectsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE);
    }
}
