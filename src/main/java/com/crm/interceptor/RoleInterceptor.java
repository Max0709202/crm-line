package com.crm.interceptor;

import com.crm.entity.AdminUser;
import com.crm.repository.AdminUserRepository;
import com.crm.service.AdminRoleService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.support.RequestContextUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Optional;

/**
 * Applies the logged-in admin's 権限 (see {@link AdminRoleService}) after {@link AuthInterceptor}:
 * <ul>
 *   <li>a session whose login was deleted / deactivated (e.g. its 権限 was removed) is logged out;</li>
 *   <li>the role is exposed to the views as {@code crmRole} (sidebar color, hidden items, masking);</li>
 *   <li>opening the page of a menu item hidden for the role goes back to 管理トップ. Only page
 *       navigations are refused — the background calls a visible page makes (e.g. the thread
 *       screen posting to /manager/messages/…) keep working.</li>
 * </ul>
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    public static final String REQUEST_ROLE = "crmRole";

    private final AdminRoleService roleService;
    private final AdminUserRepository adminUserRepository;

    public RoleInterceptor(AdminRoleService roleService, AdminUserRepository adminUserRepository) {
        this.roleService = roleService;
        this.adminUserRepository = adminUserRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        Object idAttr = session == null ? null : session.getAttribute(AuthInterceptor.SESSION_ADMIN_ID);
        if (!(idAttr instanceof Long)) return true;   // login / logout pages
        Long adminId = (Long) idAttr;

        Optional<AdminUser> admin = adminUserRepository.findById(adminId);
        if (!admin.isPresent() || !Boolean.TRUE.equals(admin.get().getIsActive())) {
            session.invalidate();
            response.sendRedirect(request.getContextPath() + "/manager/login");
            return false;
        }

        Optional<AdminRoleService.RoleView> role = roleService.viewFor(adminId);
        if (!role.isPresent()) return true;
        request.setAttribute(REQUEST_ROLE, role.get());

        String key = AdminRoleService.menuKeyForPath(request.getServletPath());
        if (key != null && role.get().isHidden(key) && isPageNavigation(request)) {
            FlashMap flash = RequestContextUtils.getOutputFlashMap(request);
            flash.put("flashError", "この権限では表示できないページです");
            RequestContextUtils.saveOutputFlashMap("/manager/dashboard", request, response);
            response.sendRedirect(request.getContextPath() + "/manager/dashboard");
            return false;
        }
        return true;
    }

    /** A browser opening a page (link, address bar, iframe) — not fetch()/XHR. */
    private static boolean isPageNavigation(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) return false;
        String mode = request.getHeader("Sec-Fetch-Mode");
        if (mode != null) return "navigate".equals(mode);
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }
}
