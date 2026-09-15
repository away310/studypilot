package com.studypilot.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * 应用内登录（替代 Nginx Basic 认证）。
 *
 * 账号密码来自环境变量（STUDYPILOT_AUTH_USER / STUDYPILOT_AUTH_PASSWORD），
 * 不写入代码与仓库；登录成功后写入 HttpSession，由 {@link AuthInterceptor} 统一校验。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String SESSION_KEY = "STUDYPILOT_USER";

    @Value("${app.auth.username:}")
    private String configUser;

    @Value("${app.auth.password:}")
    private String configPassword;

    /** 连续失败次数与锁定时间戳（简单防暴力破解，按 IP 记录）。 */
    private final java.util.Map<String, long[]> failures = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_FAILURES = 8;
    private static final long LOCK_MILLIS = 60_000;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String ip = clientIp(request);
        long[] state = failures.computeIfAbsent(ip, k -> new long[]{0, 0});
        synchronized (state) {
            long now = System.currentTimeMillis();
            if (state[1] > now) {
                long waitSec = (state[1] - now) / 1000;
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("error", "尝试过于频繁，请 " + waitSec + " 秒后再试"));
            }
        }

        String username = body.getOrDefault("username", "").strip();
        String password = body.getOrDefault("password", "");

        if (configPassword == null || configPassword.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "服务未配置访问密码（STUDYPILOT_AUTH_PASSWORD）"));
        }

        boolean ok = constantTimeEquals(username, configUser) && constantTimeEquals(password, configPassword);
        if (!ok) {
            synchronized (state) {
                state[0]++;
                if (state[0] >= MAX_FAILURES) {
                    state[1] = System.currentTimeMillis() + LOCK_MILLIS;
                    state[0] = 0;
                }
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "账号或密码错误"));
        }

        synchronized (state) {
            state[0] = 0;
            state[1] = 0;
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_KEY, username);
        session.setMaxInactiveInterval(7 * 24 * 3600); // 7 天免登录
        return ResponseEntity.ok(Map.of("authenticated", true, "username", username));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(Map.of("authenticated", false));
    }

    @GetMapping("/status")
    public Map<String, Object> status(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object user = session == null ? null : session.getAttribute(SESSION_KEY);
        return Map.of("authenticated", user != null, "username", user == null ? "" : user.toString());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Real-IP");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded;
        }
        return request.getRemoteAddr();
    }

    /** 常量时间比较，避免时序侧信道。 */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
