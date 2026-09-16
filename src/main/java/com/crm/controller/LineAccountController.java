package com.crm.controller;

import com.crm.dto.LineAccountForm;
import com.crm.entity.LineAccount;
import com.crm.interceptor.AuthInterceptor;
import com.crm.service.AuditLogService;
import com.crm.service.LineAccountService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * LINE account management (parent → up to ~100 child Official Accounts). Every mutating
 * action requires ADMIN (not OPERATOR) — LINE credentials are exactly the kind of
 * security-sensitive setting {@link AuthInterceptor#isAdmin} was added for.
 */
@Controller
@RequestMapping("/manager/line-settings")
public class LineAccountController {

    private static final String DENIED_MESSAGE = "LINE設定の変更には管理者権限が必要です";

    private final LineAccountService service;
    private final AuditLogService auditLog;
    private final com.crm.service.DomainSettingService domainSettingService;

    public LineAccountController(LineAccountService service, AuditLogService auditLog,
                                  com.crm.service.DomainSettingService domainSettingService) {
        this.service = service;
        this.auditLog = auditLog;
        this.domainSettingService = domainSettingService;
    }

    /** Returns a redirect string if the session isn't ADMIN, or null if it's fine to proceed. */
    private String denyUnlessAdmin(HttpSession session, RedirectAttributes ra) {
        if (AuthInterceptor.isAdmin(session)) return null;
        ra.addFlashAttribute("flashError", DENIED_MESSAGE);
        return "redirect:/manager/dashboard";
    }

    @GetMapping
    public String list(HttpSession session, Model model, RedirectAttributes ra) {
        String denied = denyUnlessAdmin(session, ra);
        if (denied != null) return denied;

        List<LineAccount> parents = service.listParents();
        Map<Long, List<LineAccount>> childrenByParent = new LinkedHashMap<>();
        for (LineAccount p : parents) {
            childrenByParent.put(p.getId(), service.listChildren(p.getId()));
        }
        model.addAttribute("parents", parents);
        model.addAttribute("childrenByParent", childrenByParent);
        model.addAttribute("webhookBaseUrl", domainSettingService.getReplyBaseUrl() + "/api/inbound/line/");
        return "line/account-list";
    }

    @GetMapping("/new")
    public String createForm(@RequestParam(required = false) Long parentAccountId,
                              HttpSession session, Model model, RedirectAttributes ra) {
        String denied = denyUnlessAdmin(session, ra);
        if (denied != null) return denied;

        LineAccountForm form = new LineAccountForm();
        form.setParentAccountId(parentAccountId);
        model.addAttribute("form", form);
        model.addAttribute("editing", false);
        model.addAttribute("parents", service.listParents());
        return "line/account-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") LineAccountForm form, BindingResult br,
                          HttpSession session, Model model, RedirectAttributes ra) {
        String denied = denyUnlessAdmin(session, ra);
        if (denied != null) return denied;

        if (br.hasErrors()) {
            model.addAttribute("editing", false);
            model.addAttribute("parents", service.listParents());
            return "line/account-form";
        }
        try {
            LineAccount saved = service.create(form);
            auditLog.record(AuditLogService.ACTION_LINE_ACCOUNT_CREATE, "LineAccount", saved.getId(), saved.getName());
            ra.addFlashAttribute("flashSuccess", "LINEアカウントを登録しました");
            return "redirect:/manager/line-settings";
        } catch (LineAccountService.DuplicateChannelIdException e) {
            br.rejectValue("channelId", "duplicate", "このChannel IDは既に登録されています");
        } catch (LineAccountService.MissingCredentialException e) {
            br.reject("missingCredential", "Channel SecretとAccess Tokenは新規登録時に必須です");
        } catch (LineAccountService.InvalidParentException e) {
            br.rejectValue("parentAccountId", "invalid", e.getMessage());
        }
        model.addAttribute("editing", false);
        model.addAttribute("parents", service.listParents());
        return "line/account-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        String denied = denyUnlessAdmin(session, ra);
        if (denied != null) return denied;

        Optional<LineAccount> a = service.findById(id);
        if (!a.isPresent()) {
            ra.addFlashAttribute("flashError", "LINEアカウントが見つかりません");
            return "redirect:/manager/line-settings";
        }
        model.addAttribute("form", LineAccountForm.from(a.get()));
        model.addAttribute("editing", true);
        model.addAttribute("accountId", id);
        model.addAttribute("parents", service.listParents());
        return "line/account-form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") LineAccountForm form,
                          BindingResult br, HttpSession session, Model model, RedirectAttributes ra) {
        String denied = denyUnlessAdmin(session, ra);
        if (denied != null) return denied;

        if (br.hasErrors()) {
            model.addAttribute("editing", true);
            model.addAttribute("accountId", id);
            model.addAttribute("parents", service.listParents());
            return "line/account-form";
        }
        try {
            service.update(id, form);
            auditLog.record(AuditLogService.ACTION_LINE_ACCOUNT_UPDATE, "LineAccount", id, form.getName());
            ra.addFlashAttribute("flashSuccess", "LINEアカウントを更新しました");
            return "redirect:/manager/line-settings";
        } catch (LineAccountService.DuplicateChannelIdException e) {
            br.rejectValue("channelId", "duplicate", "このChannel IDは既に登録されています");
        } catch (LineAccountService.InvalidParentException e) {
            br.rejectValue("parentAccountId", "invalid", e.getMessage());
        } catch (LineAccountService.NotFoundException e) {
            ra.addFlashAttribute("flashError", "LINEアカウントが見つかりません");
            return "redirect:/manager/line-settings";
        }
        model.addAttribute("editing", true);
        model.addAttribute("accountId", id);
        model.addAttribute("parents", service.listParents());
        return "line/account-form";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        String denied = denyUnlessAdmin(session, ra);
        if (denied != null) return denied;

        try {
            service.delete(id);
            auditLog.record(AuditLogService.ACTION_LINE_ACCOUNT_DELETE, "LineAccount", id, null);
            ra.addFlashAttribute("flashSuccess", "LINEアカウントを削除しました");
        } catch (LineAccountService.HasChildrenException e) {
            ra.addFlashAttribute("flashError", "子アカウントが残っているため削除できません。先に子アカウントを削除してください");
        } catch (LineAccountService.NotFoundException e) {
            ra.addFlashAttribute("flashError", "LINEアカウントが見つかりません");
        }
        return "redirect:/manager/line-settings";
    }

    @PostMapping("/{id}/toggle-group-mode")
    public String toggleGroupMode(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        String denied = denyUnlessAdmin(session, ra);
        if (denied != null) return denied;

        Optional<LineAccount> opt = service.findById(id);
        if (!opt.isPresent()) {
            ra.addFlashAttribute("flashError", "LINEアカウントが見つかりません");
            return "redirect:/manager/line-settings";
        }
        LineAccount a = opt.get();
        boolean newValue = !Boolean.TRUE.equals(a.getIsGroupChatMode());
        service.setGroupChatMode(id, newValue);
        auditLog.record(AuditLogService.ACTION_LINE_SETTINGS_CHANGE, "LineAccount", id,
                "isGroupChatMode=" + newValue);
        ra.addFlashAttribute("flashSuccess", newValue ? "グループLINE風モードを有効にしました" : "グループLINE風モードを無効にしました");
        return "redirect:/manager/line-settings";
    }

    @PostMapping("/{id}/check-connection")
    public String checkConnection(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        String denied = denyUnlessAdmin(session, ra);
        if (denied != null) return denied;

        try {
            LineAccount a = service.checkConnection(id);
            auditLog.record(AuditLogService.ACTION_LINE_ACCOUNT_STATUS_CHANGE, "LineAccount", id, a.getStatus());
            if (LineAccount.STATUS_ACTIVE.equals(a.getStatus())) {
                ra.addFlashAttribute("flashSuccess", "接続確認に成功しました");
            } else {
                ra.addFlashAttribute("flashError", "接続確認に失敗しました。Channel Access Tokenを確認してください");
            }
        } catch (LineAccountService.NotFoundException e) {
            ra.addFlashAttribute("flashError", "LINEアカウントが見つかりません");
        }
        return "redirect:/manager/line-settings";
    }
}
