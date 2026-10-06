package com.crm.interceptor;

import com.crm.service.AdminIpService;
import com.crm.util.LogSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

/**
 * セキュリティ設定 › 管理画面IP許可設定: while 有効, /manager/** (the login page included) answers 403
 * to an IP that isn't on the 許可するIP list.
 *
 * <p>The client IP is the address nginx itself saw ({@code X-Real-IP}, which nginx overwrites with
 * {@code $remote_addr} on every server block) — not the first {@code X-Forwarded-For} entry, which
 * nginx's {@code $proxy_add_x_forwarded_for} lets the client pre-fill.
 */
@Component
public class AdminIpInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminIpInterceptor.class);

    private final AdminIpService adminIpService;

    public AdminIpInterceptor(AdminIpService adminIpService) {
        this.adminIpService = adminIpService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String ip = clientIp(request);
        if (adminIpService.isAllowed(ip)) return true;
        log.warn("admin IP restriction: refused ip={} path={}", LogSafe.of(ip), LogSafe.of(request.getServletPath()));
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html; charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getOutputStream().write(("<!doctype html><html lang=\"ja\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"robots\" content=\"noindex\"><title>403</title></head><body style=\"font-family:sans-serif;"
                + "padding:40px;color:#334155\"><h1 style=\"font-size:20px\">アクセスが許可されていません</h1>"
                + "<p>このIPアドレスからは管理画面にアクセスできません。</p></body></html>").getBytes(StandardCharsets.UTF_8));
        return false;
    }

    /** The address nginx saw (X-Real-IP from the local proxy), else the direct peer. */
    public static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        boolean fromProxy = "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote);
        if (fromProxy) {
            String real = request.getHeader("X-Real-IP");
            if (real != null && !real.trim().isEmpty()) return real.trim();
        }
        return remote;
    }
}
