package com.crm.controller;

import com.crm.service.MessageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 受信管理 — per-user inbox triage. Shows one row per user who has inbound messages,
 * sorted newest first, with unread count and the latest subject/preview.
 */
@Controller
public class InboxController {

    private final MessageService messageService;
    private final com.crm.service.AdminAuthService adminAuthService;
    private final com.crm.service.MessageTemplateService templateService;
    private final com.crm.service.ThreadPanelService threadPanelService;

    public InboxController(MessageService messageService,
                            com.crm.service.AdminAuthService adminAuthService,
                            com.crm.service.MessageTemplateService templateService,
                            com.crm.service.ThreadPanelService threadPanelService) {
        this.messageService = messageService;
        this.adminAuthService = adminAuthService;
        this.templateService = templateService;
        this.threadPanelService = threadPanelService;
    }

    /** ★ on a 受信ボックス row (thread page): starred users are listed first in 全受信履歴. */
    @PostMapping("/manager/inbox/star")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<String> star(@RequestParam("id") Long userId,
                                                                @RequestParam String starred) {
        threadPanelService.setStarred(userId, "1".equals(starred));
        return org.springframework.http.ResponseEntity.ok("ok");
    }

    /**
     * 選択削除 on the thread page's 受信ボックス: removes the selected users from the list, the
     * same as the per-row ×. Messages are kept, so the exchange history stays intact. Returns
     * to the thread that was open (or to /manager/inbox, which opens the newest one).
     */
    @PostMapping("/manager/inbox/bulk-dismiss")
    public String bulkDismiss(@RequestParam(name = "ids", required = false) List<Long> userIds,
                              @RequestParam(name = "returnUserId", required = false) Long returnUserId,
                              RedirectAttributes ra) {
        int n = 0;
        if (userIds != null) {
            for (Long id : userIds) {
                if (id != null && messageService.dismissInboxForUser(id) > 0) n++;
            }
        }
        ra.addFlashAttribute("flashSuccess", n + " 件を受信ボックスから削除しました（やり取り履歴は残ります）");
        return returnUserId != null ? "redirect:/manager/users/" + returnUserId + "/thread" : "redirect:/manager/inbox";
    }

    @GetMapping("/manager/inbox")
    public String inbox(@RequestParam(name = "unread", required = false) String unread,
                        Model model) {
        boolean unreadOnly = "1".equals(unread) || "true".equalsIgnoreCase(unread);
        List<MessageService.InboxRow> rows = messageService.inboxByUser(unreadOnly);
        // 受信/返信管理 opens the newest conversation's thread directly; its left 受信ボックス lists
        // every user (client request 2026-10-01: this list page isn't needed). The page below is
        // only shown while there are no messages at all.
        if (!rows.isEmpty()) return "redirect:/manager/users/" + rows.get(0).getUserId() + "/thread";
        // Inbox emptied (e.g. the mails were deleted in 個別メッセージ管理): still open the
        // 受信やり取り page — on the newest conversation — instead of this old list page (2026-10-02).
        List<com.crm.entity.Message> latest = messageService.recentMessages(0, 1).getContent();
        if (!latest.isEmpty() && latest.get(0).getUserId() != null) {
            return "redirect:/manager/users/" + latest.get(0).getUserId() + "/thread";
        }
        // Totals are summed straight from the same `rows` the table renders, so this box and
        // the per-row 件数内訳 breakdown below always reconcile exactly (operator request
        // 2026-09-10: 件数内訳が一致するように).
        long totalUnread = 0L, totalWeb = 0L, totalMail = 0L, totalOut = 0L, totalSms = 0L;
        for (MessageService.InboxRow r : rows) {
            totalUnread += r.getUnreadCount();
            totalWeb += r.getWebReplyCount();
            totalMail += r.getMailReplyCount();
            totalOut += r.getOutCount();
            totalSms += r.getSmsOutCount();
        }
        model.addAttribute("rows", rows);
        model.addAttribute("unreadOnly", unreadOnly);
        model.addAttribute("totalUnread", totalUnread);
        model.addAttribute("totalWeb", totalWeb);
        model.addAttribute("totalMail", totalMail);
        model.addAttribute("totalOut", totalOut);
        model.addAttribute("totalSms", totalSms);
        // Bulk-reply panel data (operator request 2026-05-23 — same layout as the
        // single-user reply screen with template tabs + tag references).
        model.addAttribute("templates", templateService.listAll());
        model.addAttribute("templatePageTitles", templateService.listPageTitles());
        model.addAttribute("templateActivePages", templateService.listActivePageNumbers());
        model.addAttribute("builtinTags", com.crm.service.PlaceholderService.BUILTIN_TAGS);
        // User-specific tag keys are dynamic per-user, but for the bulk-reply panel we expose
        // the conventional 5-slot key names so operators can drop the tokens into the body.
        model.addAttribute("customTagTokens", java.util.Arrays.asList(
                "%amount%", "%product%", "%full_address%", "%date_jp%"));
        return "inbox/list";
    }

    /**
     * Delete all inbound (DIRECTION=IN) messages for the selected users.
     * Per-row checkbox sends userId; this removes every IN message under each one.
     */
    @PostMapping("/manager/inbox/bulk-delete")
    public String bulkDelete(@RequestParam(name = "userIds", required = false) List<Long> userIds,
                              @RequestParam(name = "confirmPassword", required = false) String confirmPassword,
                              javax.servlet.http.HttpSession session,
                              RedirectAttributes ra) {
        Long adminId = (Long) session.getAttribute(com.crm.interceptor.AuthInterceptor.SESSION_ADMIN_ID);
        if (!adminAuthService.verifyPassword(adminId, confirmPassword)) {
            ra.addFlashAttribute("flashError", "一括削除には管理者パスワードの確認が必要です");
            return "redirect:/manager/inbox";
        }
        int n = messageService.deleteInboundForUsers(userIds);
        ra.addFlashAttribute("flashSuccess", n + " 件の受信メッセージを削除しました");
        return "redirect:/manager/inbox";
    }

    /**
     * Reply to all selected users with the same subject/body. Placeholder tags
     * (%name%, %email%, %amount%, …) are substituted per-user before queueing.
     */
    /** Hard cap to prevent runaway POST bodies (DB column is TEXT 65535 bytes). */
    private static final int BULK_REPLY_BODY_MAX = 60_000;
    private static final int BULK_REPLY_SUBJ_MAX = 500;

    @PostMapping("/manager/inbox/bulk-reply")
    public String bulkReply(@RequestParam(name = "userIds", required = false) List<Long> userIds,
                             @RequestParam(name = "subject", required = false) String subject,
                             @RequestParam(name = "body",    required = false) String body,
                             RedirectAttributes ra) {
        if (subject != null && subject.length() > BULK_REPLY_SUBJ_MAX) {
            ra.addFlashAttribute("flashError", "件名は " + BULK_REPLY_SUBJ_MAX + " 文字以内で入力してください");
            return "redirect:/manager/inbox";
        }
        if (body != null && body.length() > BULK_REPLY_BODY_MAX) {
            ra.addFlashAttribute("flashError", "本文は " + BULK_REPLY_BODY_MAX + " 文字以内で入力してください");
            return "redirect:/manager/inbox";
        }
        if (userIds == null || userIds.isEmpty()) {
            ra.addFlashAttribute("flashError", "返信先のユーザーが選択されていません");
            return "redirect:/manager/inbox";
        }
        if (body == null || body.trim().isEmpty()) {
            ra.addFlashAttribute("flashError", "返信本文を入力してください");
            return "redirect:/manager/inbox";
        }
        int queued = messageService.bulkReplyToUsers(userIds, subject, body);
        ra.addFlashAttribute("flashSuccess",
                queued + " 件の返信メッセージをキューに登録しました");
        return "redirect:/manager/inbox";
    }
}
