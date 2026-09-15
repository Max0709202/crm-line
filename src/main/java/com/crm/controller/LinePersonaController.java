package com.crm.controller;

import com.crm.entity.AdminUser;
import com.crm.interceptor.AuthInterceptor;
import com.crm.repository.AdminUserRepository;
import com.crm.service.DomainSettingService;
import com.crm.service.HtmlImageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Self-service LINE persona (display name + avatar) for the support-character/group-chat
 * mode — see {@code LineAccount.isGroupChatMode}. Deliberately scoped to the CURRENT
 * session's own admin only (no target-id parameter): {@code AdminUser} has no general
 * management UI anywhere in this app, and building one is a materially larger, unrelated
 * feature this integration doesn't need.
 */
@Controller
@RequestMapping("/manager/line-settings/my-persona")
public class LinePersonaController {

    private final AdminUserRepository adminUserRepository;
    private final HtmlImageService htmlImageService;
    private final DomainSettingService domainSettingService;

    public LinePersonaController(AdminUserRepository adminUserRepository,
                                  HtmlImageService htmlImageService,
                                  DomainSettingService domainSettingService) {
        this.adminUserRepository = adminUserRepository;
        this.htmlImageService = htmlImageService;
        this.domainSettingService = domainSettingService;
    }

    @GetMapping
    public String form(HttpSession session, Model model) {
        model.addAttribute("admin", currentAdmin(session));
        return "line/persona-form";
    }

    @PostMapping
    public String save(@RequestParam(required = false) String displayName,
                        @RequestParam(value = "avatar", required = false) MultipartFile avatar,
                        HttpSession session, RedirectAttributes ra) {
        AdminUser admin = currentAdmin(session);
        if (admin == null) {
            ra.addFlashAttribute("flashError", "セッションが無効です。再度ログインしてください");
            return "redirect:/manager/line-settings";
        }
        admin.setDisplayName(displayName == null || displayName.trim().isEmpty() ? null : displayName.trim());
        if (avatar != null && !avatar.isEmpty()) {
            try {
                com.crm.entity.HtmlImage img = htmlImageService.upload(avatar, "line-persona:" + admin.getId(), admin.getName());
                admin.setAvatarUrl(domainSettingService.getReplyBaseUrl() + "/img/" + img.getId());
            } catch (HtmlImageService.HtmlImageException | IOException e) {
                ra.addFlashAttribute("flashError", "画像のアップロードに失敗しました: " + e.getMessage());
                return "redirect:/manager/line-settings/my-persona";
            }
        }
        adminUserRepository.save(admin);
        ra.addFlashAttribute("flashSuccess", "LINE表示名/アイコンを更新しました");
        return "redirect:/manager/line-settings/my-persona";
    }

    private AdminUser currentAdmin(HttpSession session) {
        Object id = session.getAttribute(AuthInterceptor.SESSION_ADMIN_ID);
        if (!(id instanceof Long)) return null;
        return adminUserRepository.findById((Long) id).orElse(null);
    }
}
