package com.crm.controller;

import com.crm.entity.AdminRole;
import com.crm.interceptor.AuthInterceptor;
import com.crm.service.AdminRoleService;
import com.crm.service.AuditLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * スタッフ管理 › 権限設定 — the client-approved screen: role list, masked user fields, menu items
 * shown per role, and each role's login ID / password. See {@link AdminRoleService}.
 */
@Controller
@RequestMapping("/manager/settings/roles")
public class RoleSettingController {

    private static final DateTimeFormatter ISO_MIN = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final AdminRoleService roleService;
    private final AuditLogService auditLog;
    private final ObjectMapper objectMapper;

    public RoleSettingController(AdminRoleService roleService, AuditLogService auditLog, ObjectMapper objectMapper) {
        this.roleService = roleService;
        this.auditLog = auditLog;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String page(Model model) throws JsonProcessingException {
        List<Map<String, Object>> menus = new ArrayList<>();
        for (AdminRoleService.MenuGroup g : AdminRoleService.MENU) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (AdminRoleService.MenuItem it : g.getItems()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", it.getKey());
                m.put("name", it.getName());
                items.add(m);
            }
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("group", g.getGroup());
            group.put("items", items);
            menus.add(group);
        }
        List<Map<String, Object>> roles = new ArrayList<>();
        for (AdminRole r : roleService.list()) roles.add(toJson(r));
        model.addAttribute("menusJson", json(menus));
        model.addAttribute("rolesJson", json(roles));
        model.addAttribute("topLocked", AdminRoleService.TOP_ROLE_LOCKED);
        model.addAttribute("colors", AdminRoleService.COLORS);
        return "setting/roles";
    }

    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> save(@RequestParam(required = false) String id,
                                                    @RequestParam(required = false) String name,
                                                    @RequestParam(required = false) String holder,
                                                    @RequestParam(required = false) String color,
                                                    @RequestParam(required = false) String loginId,
                                                    @RequestParam(required = false) List<String> mask,
                                                    @RequestParam(required = false) List<String> hidden,
                                                    @RequestParam(required = false) String password,
                                                    HttpSession session) {
        AdminRoleService.RoleInput in = new AdminRoleService.RoleInput();
        in.name = name;
        in.holder = holder;
        in.color = color;
        in.loginId = loginId;
        if (mask != null) in.mask = mask;
        if (hidden != null) in.hidden = hidden;
        in.password = password;
        Long roleId = parseId(id);
        try {
            AdminRole r = roleService.save(roleId, in);
            auditLog.record(AuditLogService.ACTION_SETTINGS_UPDATE, "AdminRole", r.getId(),
                    (roleId == null ? "権限を追加: " : "権限を更新: ") + r.getName()
                            + (password != null && !password.isEmpty() ? "（パスワード変更）" : ""));
            // the sidebar shows the logged-in name — keep it current when editing one's own role
            if (r.getAdminUserId() != null && r.getAdminUserId().equals(session.getAttribute(AuthInterceptor.SESSION_ADMIN_ID))) {
                session.setAttribute(AuthInterceptor.SESSION_ADMIN_NAME, r.getHolder() != null ? r.getHolder() : r.getName());
            }
            return ResponseEntity.ok(toJson(r));
        } catch (AdminRoleService.RoleException e) {
            return ResponseEntity.status(e.getStatus()).body(Collections.<String, Object>singletonMap("message", e.getMessage()));
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<Map<String, Object>> delete(@RequestParam String id, HttpSession session) {
        Long roleId = parseId(id);
        if (roleId == null) return ResponseEntity.badRequest().body(Collections.<String, Object>singletonMap("message", "IDが不正です"));
        Object me = session.getAttribute(AuthInterceptor.SESSION_ADMIN_ID);
        boolean own = roleService.list().stream()
                .anyMatch(r -> r.getId().equals(roleId) && r.getAdminUserId() != null && r.getAdminUserId().equals(me));
        if (own) {
            return ResponseEntity.badRequest().body(Collections.<String, Object>singletonMap("message", "ログイン中の権限は削除できません"));
        }
        try {
            roleService.delete(roleId);
            auditLog.record(AuditLogService.ACTION_SETTINGS_UPDATE, "AdminRole", roleId, "権限を削除");
            return ResponseEntity.ok(Collections.<String, Object>singletonMap("deleted", roleId));
        } catch (AdminRoleService.RoleException e) {
            return ResponseEntity.status(e.getStatus()).body(Collections.<String, Object>singletonMap("message", e.getMessage()));
        }
    }

    private Map<String, Object> toJson(AdminRole r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(r.getId()));
        m.put("name", r.getName());
        m.put("holder", r.getHolder() == null ? "" : r.getHolder());
        m.put("color", r.getColor());
        m.put("loginId", r.getLoginId());
        m.put("mask", r.getMaskList());
        m.put("hidden", r.getHiddenList());
        m.put("passwordSet", r.getAdminUserId() != null);
        m.put("passwordUpdatedAt", fmt(r.getPasswordUpdatedAt()));
        m.put("updatedAt", fmt(r.getUpdatedAt()));
        m.put("isDefault", r.isDefaultRole());
        return m;
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? "" : t.format(ISO_MIN);
    }

    private static Long parseId(String id) {
        if (id == null || id.trim().isEmpty()) return null;
        try { return Long.valueOf(id.trim()); } catch (NumberFormatException e) { return null; }
    }

    private String json(Object o) throws JsonProcessingException {
        // Safe inside <script type="application/json">: never let the data close the tag.
        return objectMapper.writeValueAsString(o).replace("</", "<\\/");
    }
}
