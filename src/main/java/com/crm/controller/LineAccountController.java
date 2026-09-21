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
    private final com.crm.repository.LineUserRepository lineUserRepository;
    private final com.crm.service.AdminAuthService adminAuthService;

    public LineAccountController(LineAccountService service, AuditLogService auditLog,
                                  com.crm.service.DomainSettingService domainSettingService,
                                  com.crm.repository.LineUserRepository lineUserRepository,
                                  com.crm.service.AdminAuthService adminAuthService) {
        this.service = service;
        this.auditLog = auditLog;
        this.domainSettingService = domainSettingService;
        this.lineUserRepository = lineUserRepository;
        this.adminAuthService = adminAuthService;
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
        Map<Long, Long> friendCounts = new LinkedHashMap<>();
        for (LineAccount p : parents) {
            List<LineAccount> children = service.listChildren(p.getId());
            childrenByParent.put(p.getId(), children);
            friendCounts.put(p.getId(), lineUserRepository.countByLineAccountId(p.getId()));
            for (LineAccount c : children) {
                friendCounts.put(c.getId(), lineUserRepository.countByLineAccountId(c.getId()));
            }
        }
        model.addAttribute("parents", parents);
        model.addAttribute("childrenByParent", childrenByParent);
        model.addAttribute("friendCounts", friendCounts);
        model.addAttribute("webhookBaseUrl", domainSettingService.getReplyBaseUrl() + "/api/inbound/line/");
        model.addAttribute("lineMaxBodyLength", domainSettingService.getLineMaxBodyLength());
        model.addAttribute("lineRatePerMinute", domainSettingService.getLineRatePerMinute());
        model.addAttribute("lineAutoRegisterFolder", domainSettingService.getLineAutoRegisterFolder());
        return "line/account-list";
    }

    /** Bulk 選択削除 — admin-gated same as every other mutating endpoint here, plus the
     *  admin-password confirmation used by the other bulk-delete screens (carrier pool, users). */
    @PostMapping("/bulk-delete")
    public String bulkDelete(@RequestParam(name = "ids", required = false) List<Long> ids,
                              @RequestParam(name = "confirmPassword", required = false) String confirmPassword,
                              HttpSession session, RedirectAttributes ra) {
        String denied = denyUnlessAdmin(session, ra);
        if (denied != null) return denied;

        Long adminId = (Long) session.getAttribute(AuthInterceptor.SESSION_ADMIN_ID);
        if (!adminAuthService.verifyPassword(adminId, confirmPassword)) {
            ra.addFlashAttribute("flashError", "一括削除には管理者パスワードの確認が必要です");
            return "redirect:/manager/line-settings";
        }
        int n = service.deleteByIds(ids);
        auditLog.record(AuditLogService.ACTION_LINE_ACCOUNT_DELETE, "LineAccount", null, "bulk n=" + n);
        ra.addFlashAttribute("flashSuccess", n + " 件のLINEアカウントを削除しました（子アカウントが残っているものはスキップされました）");
        return "redirect:/manager/line-settings";
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

    /**
     * Bulk registration of several already-manually-created child accounts' credentials at
     * once ("⇨ログイン"). LINE's Messaging API has no endpoint to programmatically create an
     * Official Account — this is the agreed substitute for that infeasible ask: paste in
     * Name/Channel ID/Secret/Token for several accounts (one per line) instead of repeating
     * the single-account form N times.
     */
    @GetMapping("/bulk-new")
    public String bulkCreateForm(@RequestParam Long parentAccountId, HttpSession session,
                                  Model model, RedirectAttributes ra) {
        String denied = denyUnlessAdmin(session, ra);
        if (denied != null) return denied;

        Optional<LineAccount> parent = service.findById(parentAccountId);
        if (!parent.isPresent()) {
            ra.addFlashAttribute("flashError", "親アカウントが見つかりません");
            return "redirect:/manager/line-settings";
        }
        model.addAttribute("parent", parent.get());
        return "line/account-bulk-form";
    }

    @PostMapping("/bulk-new")
    public String bulkCreate(@RequestParam Long parentAccountId,
                              @RequestParam String lines,
                              HttpSession session, RedirectAttributes ra) {
        String denied = denyUnlessAdmin(session, ra);
        if (denied != null) return denied;

        int ok = 0;
        List<String> errors = new java.util.ArrayList<>();
        int lineNo = 0;
        for (String raw : lines.split("\\r?\\n")) {
            lineNo++;
            String row = raw.trim();
            if (row.isEmpty()) continue;
            String[] cols = row.split(",", -1);
            if (cols.length < 4) {
                errors.add(lineNo + "行目: 列数が不足しています (name,channelId,channelSecret,accessToken)");
                continue;
            }
            LineAccountForm form = new LineAccountForm();
            form.setParentAccountId(parentAccountId);
            form.setName(cols[0].trim());
            form.setChannelId(cols[1].trim());
            form.setChannelSecret(cols[2].trim());
            form.setAccessToken(cols[3].trim());
            try {
                LineAccount saved = service.create(form);
                auditLog.record(AuditLogService.ACTION_LINE_ACCOUNT_CREATE, "LineAccount", saved.getId(), saved.getName());
                ok++;
            } catch (LineAccountService.DuplicateChannelIdException e) {
                errors.add(lineNo + "行目 (" + cols[0].trim() + "): このChannel IDは既に登録されています");
            } catch (LineAccountService.MissingCredentialException e) {
                errors.add(lineNo + "行目 (" + cols[0].trim() + "): Channel SecretとAccess Tokenは必須です");
            } catch (LineAccountService.InvalidParentException e) {
                errors.add(lineNo + "行目 (" + cols[0].trim() + "): " + e.getMessage());
            } catch (Exception e) {
                errors.add(lineNo + "行目 (" + cols[0].trim() + "): 登録に失敗しました");
            }
        }
        if (ok > 0) {
            ra.addFlashAttribute("flashSuccess", ok + " 件の子アカウントを登録しました");
        }
        if (!errors.isEmpty()) {
            ra.addFlashAttribute("flashError", "以下の行はスキップされました: " + String.join(" / ", errors));
        }
        return "redirect:/manager/line-settings";
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

    @PostMapping("/max-body-length")
    public String saveMaxBodyLength(@RequestParam int value, HttpSession session, RedirectAttributes ra) {
        String denied = denyUnlessAdmin(session, ra);
        if (denied != null) return denied;

        int clamped = Math.max(1, Math.min(5000, value));
        domainSettingService.save(com.crm.service.DomainSettingService.KEY_LINE_MAX_BODY_LENGTH, String.valueOf(clamped));
        auditLog.record(AuditLogService.ACTION_LINE_SETTINGS_CHANGE, "CrmSetting", null,
                "line.max_body_length=" + clamped);
        ra.addFlashAttribute("flashSuccess", "LINE本文の最大文字数を更新しました");
        return "redirect:/manager/line-settings";
    }

    @PostMapping("/rate-per-minute")
    public String saveRatePerMinute(@RequestParam int value, HttpSession session, RedirectAttributes ra) {
        String denied = denyUnlessAdmin(session, ra);
        if (denied != null) return denied;

        domainSettingService.setLineRatePerMinute(value);
        auditLog.record(AuditLogService.ACTION_LINE_SETTINGS_CHANGE, "CrmSetting", null,
                "line.rate_per_minute=" + domainSettingService.getLineRatePerMinute());
        ra.addFlashAttribute("flashSuccess", "LINE送信間隔を更新しました");
        return "redirect:/manager/line-settings";
    }

    @PostMapping("/auto-register-folder")
    public String saveAutoRegisterFolder(@RequestParam(required = false) String folder,
                                          HttpSession session, RedirectAttributes ra) {
        String denied = denyUnlessAdmin(session, ra);
        if (denied != null) return denied;

        String trimmed = (folder == null) ? "" : folder.trim();
        domainSettingService.save(com.crm.service.DomainSettingService.KEY_LINE_AUTO_REGISTER_FOLDER, trimmed);
        auditLog.record(AuditLogService.ACTION_LINE_SETTINGS_CHANGE, "CrmSetting", null,
                "line.auto_register_folder=" + trimmed);
        ra.addFlashAttribute("flashSuccess", "LINE自動登録フォルダを更新しました");
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

    /**
     * Diagnostic-only: reports the stored Channel Secret's shape (length, whitespace,
     * hex-format) without ever exposing or logging the value itself. Added 2026-09-21 to
     * debug a client's repeated signature-verification failures — a copy-paste error
     * (extra whitespace/newline, or the wrong credential entirely) is invisible from the
     * edit form alone, since that field is deliberately never round-tripped to the UI.
     */
    @PostMapping("/{id}/check-secret-format")
    public String checkSecretFormat(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        String denied = denyUnlessAdmin(session, ra);
        if (denied != null) return denied;

        Optional<LineAccount> opt = service.findById(id);
        if (!opt.isPresent()) {
            ra.addFlashAttribute("flashError", "LINEアカウントが見つかりません");
            return "redirect:/manager/line-settings";
        }
        String secret = service.decryptChannelSecret(opt.get());
        int len = secret == null ? 0 : secret.length();
        boolean leadingWs = secret != null && !secret.isEmpty() && Character.isWhitespace(secret.charAt(0));
        boolean trailingWs = secret != null && !secret.isEmpty() && Character.isWhitespace(secret.charAt(secret.length() - 1));
        boolean looksLikeHex = secret != null && secret.matches("^[0-9a-fA-F]+$");
        String msg = "Channel Secret形式チェック: 文字数=" + len
                + " / 先頭に空白=" + (leadingWs ? "あり" : "なし")
                + " / 末尾に空白=" + (trailingWs ? "あり" : "なし")
                + " / 16進数のみで構成=" + (looksLikeHex ? "はい" : "いいえ");
        ra.addFlashAttribute("flashSuccess", msg);
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
