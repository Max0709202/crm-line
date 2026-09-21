package com.crm.controller;

import com.crm.service.AuditLogService;
import com.crm.service.LineUserLinkService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Manual linking screen for LINE contacts LINE's webhook couldn't auto-match to a customer.
 * Not admin-gated — linking a contact is an ordinary operational task (like the rest of
 * customer management), unlike LINE account/credential settings in {@link LineAccountController}.
 */
@Controller
@RequestMapping("/manager/line-settings/unmatched")
public class LineUserLinkController {

    private final LineUserLinkService service;
    private final AuditLogService auditLog;
    private final com.crm.repository.LineAccountRepository lineAccountRepository;
    private final com.crm.service.AdminAuthService adminAuthService;

    public LineUserLinkController(LineUserLinkService service, AuditLogService auditLog,
                                   com.crm.repository.LineAccountRepository lineAccountRepository,
                                   com.crm.service.AdminAuthService adminAuthService) {
        this.service = service;
        this.auditLog = auditLog;
        this.lineAccountRepository = lineAccountRepository;
        this.adminAuthService = adminAuthService;
    }

    @GetMapping
    public String list(Model model) {
        java.util.List<com.crm.entity.LineUser> unmatched = service.listUnlinked();
        model.addAttribute("unmatched", unmatched);
        // Which LINE account (name) each contact came in through — with 2+ registered
        // accounts, the admin needs this to make sense of an otherwise-unlabeled contact.
        java.util.Map<Long, String> accountNames = new java.util.HashMap<>();
        for (com.crm.entity.LineAccount a : lineAccountRepository.findAllById(
                unmatched.stream().map(com.crm.entity.LineUser::getLineAccountId).collect(java.util.stream.Collectors.toSet()))) {
            accountNames.put(a.getId(), a.getName());
        }
        model.addAttribute("accountNames", accountNames);
        model.addAttribute("suggestions", service.suggestMatches());
        return "line/unmatched-contacts";
    }

    /** Bulk-approve display-name-matched candidates (checkboxes on the unmatched-contacts
     *  page). Each checkbox's value is "{lineUserId}:{crmUserId}" — a single combined value
     *  per checkbox, not two parallel arrays, so an admin unchecking some suggestions can't
     *  desync a lineUserId from the wrong crmUserId. */
    @PostMapping("/bulk-link")
    public String bulkLink(@RequestParam(name = "pair", required = false) java.util.List<String> pairs,
                            RedirectAttributes ra) {
        java.util.Map<Long, Long> parsed = new java.util.LinkedHashMap<>();
        if (pairs != null) {
            for (String p : pairs) {
                String[] parts = p.split(":", 2);
                if (parts.length != 2) continue;
                try {
                    parsed.put(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
                } catch (NumberFormatException ignore) {}
            }
        }
        int n = service.linkAll(parsed);
        for (Long lineUserId : parsed.keySet()) {
            auditLog.record(AuditLogService.ACTION_LINE_USER_LINK, "LineUser", lineUserId, "bulk crmUserId=" + parsed.get(lineUserId));
        }
        ra.addFlashAttribute("flashSuccess", n + " 件を紐付けました");
        return "redirect:/manager/line-settings/unmatched";
    }

    /** 選択削除 — removes unlinked contacts so they can re-register via a fresh friend-add.
     *  Same admin-password confirmation as the other bulk-delete screens. */
    @PostMapping("/bulk-delete")
    public String bulkDelete(@RequestParam(name = "ids", required = false) java.util.List<Long> ids,
                              @RequestParam(name = "confirmPassword", required = false) String confirmPassword,
                              javax.servlet.http.HttpSession session,
                              RedirectAttributes ra) {
        Long adminId = (Long) session.getAttribute(com.crm.interceptor.AuthInterceptor.SESSION_ADMIN_ID);
        if (!adminAuthService.verifyPassword(adminId, confirmPassword)) {
            ra.addFlashAttribute("flashError", "削除には管理者パスワードの確認が必要です");
            return "redirect:/manager/line-settings/unmatched";
        }
        int n = service.deleteUnlinkedByIds(ids);
        auditLog.record(AuditLogService.ACTION_LINE_SETTINGS_CHANGE, "LineUser", null, "bulk delete unlinked n=" + n);
        ra.addFlashAttribute("flashSuccess", n + " 件の未紐付け連絡先を削除しました");
        return "redirect:/manager/line-settings/unmatched";
    }

    @PostMapping("/{id}/link")
    public String link(@PathVariable Long id, @RequestParam Long crmUserId, RedirectAttributes ra) {
        try {
            service.link(id, crmUserId);
            auditLog.record(AuditLogService.ACTION_LINE_USER_LINK, "LineUser", id, "crmUserId=" + crmUserId);
            ra.addFlashAttribute("flashSuccess", "顧客と紐付けました");
        } catch (LineUserLinkService.NotFoundException | LineUserLinkService.CrmUserNotFoundException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/manager/line-settings/unmatched";
    }
}
