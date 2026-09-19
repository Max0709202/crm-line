package com.crm.controller;

import com.crm.dto.LineAutoReplyRuleForm;
import com.crm.entity.LineAccount;
import com.crm.interceptor.AuthInterceptor;
import com.crm.service.AuditLogService;
import com.crm.service.LineAccountService;
import com.crm.service.LineAutoReplyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.Optional;

/** Follow/keyword auto-reply rule management for one LINE account. Admin-gated, same as
 *  every other LINE credential/settings screen (see {@link LineAccountController}). */
@Controller
@RequestMapping("/manager/line-settings/{accountId}/auto-reply")
public class LineAutoReplyController {

    private static final String DENIED_MESSAGE = "LINE設定の変更には管理者権限が必要です";

    private final LineAutoReplyService service;
    private final LineAccountService accountService;
    private final AuditLogService auditLog;

    public LineAutoReplyController(LineAutoReplyService service, LineAccountService accountService,
                                    AuditLogService auditLog) {
        this.service = service;
        this.accountService = accountService;
        this.auditLog = auditLog;
    }

    private String denyUnlessAdmin(HttpSession session, RedirectAttributes ra) {
        if (AuthInterceptor.isAdmin(session)) return null;
        ra.addFlashAttribute("flashError", DENIED_MESSAGE);
        return "redirect:/manager/dashboard";
    }

    @GetMapping
    public String list(@PathVariable Long accountId, HttpSession session, Model model, RedirectAttributes ra) {
        String denied = denyUnlessAdmin(session, ra);
        if (denied != null) return denied;

        Optional<LineAccount> account = accountService.findById(accountId);
        if (!account.isPresent()) {
            ra.addFlashAttribute("flashError", "LINEアカウントが見つかりません");
            return "redirect:/manager/line-settings";
        }
        model.addAttribute("account", account.get());
        model.addAttribute("rules", service.list(accountId));
        if (!model.containsAttribute("form")) model.addAttribute("form", new LineAutoReplyRuleForm());
        return "line/auto-reply-list";
    }

    @PostMapping
    public String create(@PathVariable Long accountId, @Valid @ModelAttribute("form") LineAutoReplyRuleForm form,
                          BindingResult br, HttpSession session, Model model, RedirectAttributes ra) {
        String denied = denyUnlessAdmin(session, ra);
        if (denied != null) return denied;

        if (!br.hasErrors()) {
            try {
                service.create(accountId, form);
                auditLog.record(AuditLogService.ACTION_LINE_SETTINGS_CHANGE, "LineAutoReplyRule", accountId,
                        "created trigger=" + form.getTriggerType());
                ra.addFlashAttribute("flashSuccess", "自動返信ルールを追加しました");
                return "redirect:/manager/line-settings/" + accountId + "/auto-reply";
            } catch (LineAutoReplyService.InvalidRuleException e) {
                br.reject("invalid", e.getMessage());
            }
        }
        Optional<LineAccount> account = accountService.findById(accountId);
        model.addAttribute("account", account.orElse(null));
        model.addAttribute("rules", service.list(accountId));
        return "line/auto-reply-list";
    }

    @PostMapping("/{id}/toggle-active")
    public String toggleActive(@PathVariable Long accountId, @PathVariable Long id,
                                HttpSession session, RedirectAttributes ra) {
        String denied = denyUnlessAdmin(session, ra);
        if (denied != null) return denied;

        service.toggleActive(id);
        return "redirect:/manager/line-settings/" + accountId + "/auto-reply";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long accountId, @PathVariable Long id,
                          HttpSession session, RedirectAttributes ra) {
        String denied = denyUnlessAdmin(session, ra);
        if (denied != null) return denied;

        service.delete(id);
        auditLog.record(AuditLogService.ACTION_LINE_SETTINGS_CHANGE, "LineAutoReplyRule", id, "deleted");
        ra.addFlashAttribute("flashSuccess", "自動返信ルールを削除しました");
        return "redirect:/manager/line-settings/" + accountId + "/auto-reply";
    }
}
