package com.crm.service;

import com.crm.interceptor.RoleInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * User fields as the logged-in 権限 may see them — the templates call
 * {@code ${@roleMask.email(u.email)}} etc. Values come back unchanged for a role that doesn't
 * mask that field (or outside a request, e.g. scheduled jobs).
 */
@Service("roleMask")
public class RoleMaskService {

    public boolean masks(String field) {
        return isMasked(field);
    }

    /** Whether the current request's 権限 masks {@code field} (address / email / phone). */
    public static boolean isMasked(String field) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        Object role = attrs == null ? null : attrs.getAttribute(RoleInterceptor.REQUEST_ROLE, RequestAttributes.SCOPE_REQUEST);
        return role instanceof AdminRoleService.RoleView && ((AdminRoleService.RoleView) role).isMasked(field);
    }

    public String email(String v) {
        return masks("email") ? AdminRoleService.maskEmail(v) : v;
    }

    public String phone(String v) {
        return masks("phone") ? AdminRoleService.maskPhone(v) : v;
    }

    public String address(String v) {
        return masks("address") ? AdminRoleService.maskAddress(v) : v;
    }
}
