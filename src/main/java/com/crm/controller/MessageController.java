package com.crm.controller;

import com.crm.dto.MessageComposeForm;
import com.crm.entity.CrmUser;
import com.crm.entity.Message;
import com.crm.interceptor.AuthInterceptor;
import com.crm.service.CarrierBindingService;
import com.crm.service.CrmUserService;
import com.crm.service.MessageService;
import com.crm.service.MessageTemplateService;
import com.crm.service.PlaceholderService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.List;
import java.util.Optional;

@Controller
public class MessageController {

    private final MessageService messageService;
    private final CrmUserService userService;
    private final PlaceholderService placeholderService;
    private final MessageTemplateService templateService;
    private final CarrierBindingService bindingService;
    private final com.crm.service.AdminAuthService adminAuthService;
    private final com.crm.service.PaymentService paymentService;
    private final com.crm.repository.ReplyPageAttachmentRepository attachmentRepo;
    private final com.crm.service.ReplyPageSettingService replyPageSettingService;
    private final com.crm.service.DiffScheduleService diffScheduleService;
    private final com.crm.service.AuditLogService auditLog;
    private final com.crm.repository.LineUserRepository lineUserRepository;
    private final com.crm.service.DomainSettingService domainSettingService;
    private final com.crm.repository.LineAccountRepository lineAccountRepository;
    private final com.crm.service.ReplyHtmlSlotService replyHtmlSlotService;
    private final com.crm.service.ThreadLayoutService threadLayoutService;
    private final com.crm.service.ThreadPanelService threadPanelService;
    private final com.crm.service.FolderSettingService folderSettingService;
    private final com.crm.service.CharaLinkService charaLinkService;
    private final com.crm.repository.CharaRepository charaRepository;
    private final com.crm.repository.CharaFolderRepository charaFolderRepository;
    private final com.crm.service.UserProfileService userProfileService;
    private final com.crm.service.UserPointService userPointService;
    private final com.crm.service.LineBlockService lineBlockService;
    private final com.crm.service.LineAccountPhotoService lineAccountPhotoService;

    public MessageController(MessageService messageService,
                             CrmUserService userService,
                             PlaceholderService placeholderService,
                             MessageTemplateService templateService,
                             CarrierBindingService bindingService,
                             com.crm.service.AdminAuthService adminAuthService,
                             com.crm.service.PaymentService paymentService,
                             com.crm.repository.ReplyPageAttachmentRepository attachmentRepo,
                             com.crm.service.ReplyPageSettingService replyPageSettingService,
                             com.crm.service.DiffScheduleService diffScheduleService,
                             com.crm.service.AuditLogService auditLog,
                             com.crm.repository.LineUserRepository lineUserRepository,
                             com.crm.service.DomainSettingService domainSettingService,
                             com.crm.repository.LineAccountRepository lineAccountRepository,
                             com.crm.service.ReplyHtmlSlotService replyHtmlSlotService,
                             com.crm.service.ThreadLayoutService threadLayoutService,
                             com.crm.service.ThreadPanelService threadPanelService,
                             com.crm.service.FolderSettingService folderSettingService,
                             com.crm.service.CharaLinkService charaLinkService,
                             com.crm.repository.CharaRepository charaRepository,
                             com.crm.repository.CharaFolderRepository charaFolderRepository,
                             com.crm.service.UserProfileService userProfileService,
                             com.crm.service.UserPointService userPointService,
                             com.crm.service.LineBlockService lineBlockService,
                             com.crm.service.LineAccountPhotoService lineAccountPhotoService) {
        this.messageService = messageService;
        this.userService = userService;
        this.placeholderService = placeholderService;
        this.templateService = templateService;
        this.bindingService = bindingService;
        this.adminAuthService = adminAuthService;
        this.paymentService = paymentService;
        this.attachmentRepo = attachmentRepo;
        this.replyPageSettingService = replyPageSettingService;
        this.diffScheduleService = diffScheduleService;
        this.auditLog = auditLog;
        this.lineUserRepository = lineUserRepository;
        this.domainSettingService = domainSettingService;
        this.lineAccountRepository = lineAccountRepository;
        this.replyHtmlSlotService = replyHtmlSlotService;
        this.threadLayoutService = threadLayoutService;
        this.threadPanelService = threadPanelService;
        this.folderSettingService = folderSettingService;
        this.charaLinkService = charaLinkService;
        this.charaRepository = charaRepository;
        this.charaFolderRepository = charaFolderRepository;
        this.userProfileService = userProfileService;
        this.userPointService = userPointService;
        this.lineBlockService = lineBlockService;
        this.lineAccountPhotoService = lineAccountPhotoService;
    }

    /** Global recent-messages list with tab filtering. */
    @GetMapping("/manager/messages")
    public String list(@RequestParam(name = "page", defaultValue = "0") int page,
                       @RequestParam(name = "tab", required = false) String tab,
                       @RequestParam(name = "lineAccountId", required = false) Long lineAccountId,
                       Model model) {
        Page<Message> messages = messageService.recentMessages(page, 100, tab, lineAccountId);
        model.addAttribute("messages", messages);
        model.addAttribute("tab", tab == null ? "all" : tab);
        model.addAttribute("lineAccountId", lineAccountId);
        // Resolve userId -> email for the ユーザー column so admin sees the address, not a number.
        java.util.Set<Long> uids = new java.util.HashSet<>();
        for (Message m : messages.getContent()) if (m.getUserId() != null) uids.add(m.getUserId());
        java.util.Map<Long, String> userEmails       = new java.util.HashMap<>();
        java.util.Map<Long, String> userPhones       = new java.util.HashMap<>();
        java.util.Map<Long, String> userDisplayNames = new java.util.HashMap<>();
        java.util.Map<Long, String> userAdCodes      = new java.util.HashMap<>();
        java.util.Map<Long, String> userFolders      = new java.util.HashMap<>();
        if (!uids.isEmpty()) {
            for (CrmUser u : userService.findAllByIds(uids)) {
                userEmails.put(u.getId(), u.getEmail());
                if (u.getPhoneNumber() != null) userPhones.put(u.getId(), u.getPhoneNumber());
                if (u.getDisplayName() != null && !u.getDisplayName().isEmpty()) {
                    userDisplayNames.put(u.getId(), u.getDisplayName());
                }
                if (u.getAdCode() != null) userAdCodes.put(u.getId(), u.getAdCode());
                if (u.getFolder() != null) userFolders.put(u.getId(), u.getFolder());
            }
        }
        model.addAttribute("userEmails", userEmails);
        model.addAttribute("userPhones", userPhones);
        model.addAttribute("userDisplayNames", userDisplayNames);
        model.addAttribute("userAdCodes", userAdCodes);
        model.addAttribute("userFolders", userFolders);
        // LINE No. column: users on this page who are linked to a LINE account.
        model.addAttribute("lineLinkedUserIds", uids.isEmpty()
                ? java.util.Collections.emptySet()
                : new java.util.HashSet<>(lineUserRepository.findLinkedCrmUserIds(uids)));
        // キャラ名 column: LINE account (character) each LINE message was sent / received on.
        java.util.Set<Long> lineAccountIds = new java.util.HashSet<>();
        for (Message m : messages.getContent()) if (m.getLineAccountId() != null) lineAccountIds.add(m.getLineAccountId());
        java.util.Map<Long, String> lineAccountNames = new java.util.HashMap<>();
        if (!lineAccountIds.isEmpty()) {
            for (com.crm.entity.LineAccount a : lineAccountRepository.findAllById(lineAccountIds)) {
                lineAccountNames.put(a.getId(), a.getName());
            }
        }
        model.addAttribute("lineAccountNames", lineAccountNames);
        return "message/list";
    }

    @PostMapping("/manager/messages/bulk-delete")
    public String bulkDelete(@RequestParam(name = "ids", required = false) List<Long> ids,
                              @RequestParam(name = "tab", required = false) String tab,
                              @RequestParam(name = "confirmPassword", required = false) String confirmPassword,
                              javax.servlet.http.HttpSession session,
                              RedirectAttributes ra) {
        Long adminId = (Long) session.getAttribute(com.crm.interceptor.AuthInterceptor.SESSION_ADMIN_ID);
        String back = "redirect:/manager/messages" + (tab != null && !tab.isEmpty() ? ("?tab=" + tab) : "");
        if (!adminAuthService.verifyPassword(adminId, confirmPassword)) {
            ra.addFlashAttribute("flashError", "一括削除には管理者パスワードの確認が必要です");
            return back;
        }
        int n = messageService.deleteByIds(ids);
        ra.addFlashAttribute("flashSuccess", n + " 件のメッセージを削除しました");
        return back;
    }

    /** Chat-style thread view for one user, with inline compose form. */
    @GetMapping("/manager/users/{userId}/thread")
    public String thread(@PathVariable Long userId,
                         @RequestParam(name = "replyTo", required = false) Long replyTo,
                         @RequestParam(name = "chara", required = false) Long charaParam,
                         @RequestParam(name = "line", required = false) Long lineParam,
                         Model model, RedirectAttributes ra, HttpSession session) {
        Optional<CrmUser> user = userService.findById(userId);
        if (!user.isPresent()) {
            ra.addFlashAttribute("flashError", "ユーザーが見つかりません");
            return "redirect:/manager/users";
        }
        // Mark inbound as read when admin opens the thread (drives dashboard unread-count)
        messageService.markThreadAsRead(userId);
        List<Message> thread = messageService.threadFor(userId);
        // 紐づきキャラ をクリック → ?chara=ID: this user × キャラ only (messages sent as / to it),
        // and replies go out as that キャラ.
        com.crm.entity.Chara viewChara = charaParam == null ? null : charaRepository.findById(charaParam).orElse(null);
        if (viewChara != null) {
            java.util.Map<Long, Long> charaOf = charaLinkService.charaIdsOfMessages(thread);
            List<Message> only = new java.util.ArrayList<>();
            for (Message m : thread) if (viewChara.getId().equals(charaOf.get(m.getId()))) only.add(m);
            thread = only;
        }
        // Compute per-user thread stats for pane-tr header
        long threadWebReply = 0, threadMailReply = 0, threadOut = 0;
        for (com.crm.entity.Message m : thread) {
            if (com.crm.entity.Message.DIR_IN.equals(m.getDirection())) {
                if ("WEB_REPLY".equals(m.getChannel())) threadWebReply++;
                else if ("EMAIL".equals(m.getChannel())) threadMailReply++;
            } else if (com.crm.entity.Message.DIR_OUT.equals(m.getDirection())) {
                threadOut++;
            }
        }
        java.math.BigDecimal totalPaid = paymentService.sumPaidByUser(userId);

        model.addAttribute("user", user.get());
        model.addAttribute("thread", thread);
        model.addAttribute("threadWebReply", threadWebReply);
        model.addAttribute("threadMailReply", threadMailReply);
        model.addAttribute("threadOut", threadOut);
        model.addAttribute("totalPaid", totalPaid != null ? totalPaid : java.math.BigDecimal.ZERO);
        model.addAttribute("bindings", placeholderService.buildBindings(user.get()));
        model.addAttribute("builtinTags", PlaceholderService.BUILTIN_TAGS);
        model.addAttribute("urlLeadText", replyPageSettingService.getOrCreate().getUrlLeadText());
        model.addAttribute("templates", templateService.listAll());
        // Page-tab strip data for the templates panel: titles + active page numbers.
        model.addAttribute("templatePageTitles", templateService.listPageTitles());
        model.addAttribute("templateActivePages", templateService.listActivePageNumbers());
        model.addAttribute("boundAddresses", bindingService.listBoundFor(userId));
        // 差分予約 panel: pending (not-yet-fired) diff-schedule sends for this user, shown
        // separately from normal 予約送信 (2026-09-09 operator request).
        List<com.crm.entity.DiffScheduleStep> diffReservations =
                diffScheduleService.listPendingMessageStepsForUser(userId);
        java.util.Map<Long, com.crm.entity.DiffSchedule> diffSchedulesById = new java.util.HashMap<>();
        for (com.crm.entity.DiffScheduleStep s : diffReservations) {
            diffSchedulesById.computeIfAbsent(s.getDiffScheduleId(),
                    sid -> diffScheduleService.findScheduleById(sid).orElse(null));
        }
        model.addAttribute("diffReservations", diffReservations);
        model.addAttribute("diffSchedulesById", diffSchedulesById);
        // 紐づいているLINEアカウント名 — shown next to each LINE-channel history row (both past
        // messages and pending 差分予約 cards) so the admin doesn't have to cross-check the
        // account separately (2026-09-23 client request).
        java.util.Set<Long> lineAccountIds = new java.util.HashSet<>();
        for (com.crm.entity.Message m : thread) {
            if (m.getLineAccountId() != null) lineAccountIds.add(m.getLineAccountId());
        }
        for (com.crm.entity.DiffScheduleStep s : diffReservations) {
            if (s.getLineAccountId() != null) lineAccountIds.add(s.getLineAccountId());
        }
        java.util.Map<Long, String> lineAccountNamesById = new java.util.HashMap<>();
        if (!lineAccountIds.isEmpty()) {
            for (com.crm.entity.LineAccount a : lineAccountRepository.findAllById(lineAccountIds)) {
                lineAccountNamesById.put(a.getId(), a.getName());
            }
        }
        model.addAttribute("lineAccountNamesById", lineAccountNamesById);
        // Inbound attachment thumbnails — fetch every attachment linked to any IN-message
        // in this thread, group by message_id so the template can render the badge + grid.
        java.util.Map<Long, java.util.List<com.crm.entity.ReplyPageAttachment>> attsByMsg
                = new java.util.HashMap<>();
        java.util.List<Long> inMsgIds = new java.util.ArrayList<>();
        for (com.crm.entity.Message m : thread) {
            if (com.crm.entity.Message.DIR_IN.equals(m.getDirection())) inMsgIds.add(m.getId());
        }
        if (!inMsgIds.isEmpty()) {
            for (com.crm.entity.ReplyPageAttachment a : attachmentRepo.findByMessageIdIn(inMsgIds)) {
                attsByMsg.computeIfAbsent(a.getMessageId(), k -> new java.util.ArrayList<>()).add(a);
            }
        }
        model.addAttribute("attachmentsByMessageId", attsByMsg);
        // Left-upper inbox list (all users with any inbound, newest first).
        List<MessageService.InboxRow> inboxRows = messageService.inboxByUser(false);
        model.addAttribute("inboxRows", inboxRows);
        // 受信ボックス table columns (2026-10-03 layout): フォルダ名 / 性別色 / ログイン日 per row, ★.
        java.util.Set<Long> inboxUserIds = new java.util.HashSet<>();
        for (MessageService.InboxRow r : inboxRows) inboxUserIds.add(r.getUserId());
        java.util.Map<Long, CrmUser> inboxUsers = new java.util.HashMap<>();
        for (CrmUser u : userService.findAllByIds(inboxUserIds)) inboxUsers.put(u.getId(), u);
        model.addAttribute("inboxUsers", inboxUsers);
        model.addAttribute("starredUserIds", threadPanelService.starredUserIds());
        model.addAttribute("folderColors", folderSettingService.colorMap());
        // Gates the LINE返信 button — LINE only lets you message someone who has already
        // followed the Official Account (see LineWebhookService's javadoc).
        // Most recently messaged first: that character is the default sender.
        List<com.crm.entity.LineUser> lineLinks = lineUserRepository.findByCrmUserIdInOrderByLastMessageAtDesc(
                java.util.Collections.singletonList(userId));
        model.addAttribute("hasLineLink", !lineLinks.isEmpty());
        // 送信キャラ selector next to LINE返信 — a customer friended with several characters
        // picks which one replies (2026-09-29 client request).
        java.util.Map<Long, String> linkedCharNames = new java.util.LinkedHashMap<>();
        if (!lineLinks.isEmpty()) {
            java.util.Map<Long, String> names = new java.util.HashMap<>();
            java.util.List<Long> ids = new java.util.ArrayList<>();
            for (com.crm.entity.LineUser lu : lineLinks) ids.add(lu.getLineAccountId());
            for (com.crm.entity.LineAccount a : lineAccountRepository.findAllById(ids)) names.put(a.getId(), a.getName());
            for (Long id : ids) linkedCharNames.put(id, names.getOrDefault(id, "不明"));
        }
        model.addAttribute("linkedCharNames", linkedCharNames);
        // ?line=ID (LINE の紐づきキャラ をクリック): that character is preselected as the LINE sender.
        model.addAttribute("selectedLineAccountId", lineParam != null && linkedCharNames.containsKey(lineParam) ? lineParam : null);
        // 専用HTML (使用中) — the reply-page HTML this character is showing the customer; its
        // title is linked directly above the exchange so the operator notices it while
        // replying (2026-09-29/30 client request). Only when the in-use slot has content.
        int activeSlot = user.get().getActiveMemoSlot();
        String activeMemo = user.get().getMemoSlot(activeSlot);
        if (activeMemo != null && !activeMemo.trim().isEmpty()) {
                String circled = com.crm.service.ReplyHtmlSlotService.circled(activeSlot);
            String title = replyHtmlSlotService.getSlotTitle(activeSlot);
            model.addAttribute("activeMemoLabel", title.startsWith(circled) ? title : circled + " " + title);
        }
        model.addAttribute("lineMaxBodyLength", domainSettingService.getLineMaxBodyLength());
        if (!model.containsAttribute("form")) {
            MessageComposeForm form = new MessageComposeForm();
            if (replyTo != null) {
                // Pre-fill with reply context
                thread.stream()
                        .filter(m -> replyTo.equals(m.getId()) && Message.DIR_IN.equals(m.getDirection()))
                        .findFirst()
                        .ifPresent(original -> {
                            form.setReplyToMessageId(original.getId());
                            String subj = original.getSubject() == null ? "" : original.getSubject();
                            if (!subj.startsWith("Re:")) subj = "Re: " + subj;
                            form.setSubject(subj);
                        });
            }
            model.addAttribute("form", form);
        }
        // 画面レイアウト保存 — this admin's saved layout (sashes, memo heights, cards), null = default.
        model.addAttribute("threadLayout", threadLayoutService.get(currentAdminId(session)));
        // 右上 user card (2026-10-03): 最終送信日 = the user's latest message to us, 受信/送信 counts,
        // and the newest OUT that answers them (inbound after it is shown as 未対応).
        java.time.LocalDateTime lastInboundAt = null;
        java.time.LocalDateTime latestOutAt = null;
        long inCount = 0, outCount = 0;
        for (Message m : thread) {
            if (Message.DIR_IN.equals(m.getDirection())) {
                inCount++;
                if (lastInboundAt == null || m.getCreatedAt().isAfter(lastInboundAt)) lastInboundAt = m.getCreatedAt();
            } else if (Message.DIR_OUT.equals(m.getDirection())) {
                outCount++;
                // Same rule as the 受信ボックス 未対応 mark (MessageRepository#inboxGroupByUser): any OUT.
                if (latestOutAt == null || m.getCreatedAt().isAfter(latestOutAt)) latestOutAt = m.getCreatedAt();
            }
        }
        model.addAttribute("lastInboundAt", lastInboundAt);
        model.addAttribute("latestOutAt", latestOutAt);
        model.addAttribute("threadInCount", inCount);
        model.addAttribute("threadOutCount", outCount);
        // 送信経路 (2026-10-03): a sent mail is 直アド only when it went out from a 割り当てアドレス;
        // otherwise (base-domain FROM, the reply goes through the reply page) it is Web. Pending
        // 差分予約 mail follows the same rule via the user's current binding.
        java.util.Map<String, Boolean> poolByFrom = new java.util.HashMap<>();
        java.util.Set<Long> directOutIds = new java.util.HashSet<>();
        for (Message m : thread) {
            if (Message.DIR_OUT.equals(m.getDirection()) && Message.CHANNEL_EMAIL.equals(m.getChannel())
                    && m.getFromAddress() != null
                    && poolByFrom.computeIfAbsent(m.getFromAddress(), bindingService::isPoolAddress)) {
                directOutIds.add(m.getId());
            }
        }
        model.addAttribute("directOutIds", directOutIds);
        com.crm.entity.CarrierAddressPool boundPool = bindingService.firstBoundFor(userId).orElse(null);
        model.addAttribute("userHasPool", boundPool != null && !Boolean.FALSE.equals(boundPool.getIsActive()));
        // キャラ card: the キャラ of ?chara=, else the user's newest 紐づきキャラ (メール); replies from
        // this page go out as that キャラ. Without one, the LINE character the user talks to (if any).
        // Its やり取りメモ is still kept under キャラ ID 0.
        List<com.crm.entity.Chara> linkedCharas = charaLinkService.linkedCharas(userId);
        com.crm.entity.Chara cardChara = viewChara != null ? viewChara : (linkedCharas.isEmpty() ? null : linkedCharas.get(0));
        model.addAttribute("linkedCharas", linkedCharas);
        model.addAttribute("viewChara", viewChara);
        model.addAttribute("cardChara", cardChara);
        model.addAttribute("cardCharaFolder", cardChara == null || cardChara.getFolderId() == null ? null
                : charaFolderRepository.findById(cardChara.getFolderId()).map(com.crm.entity.CharaFolder::getName).orElse(null));
        String lineCharName = linkedCharNames.isEmpty() ? null
                : (lineParam != null && linkedCharNames.containsKey(lineParam) ? linkedCharNames.get(lineParam) : linkedCharNames.values().iterator().next());
        model.addAttribute("charName", cardChara != null ? cardChara.getName() : lineCharName);
        model.addAttribute("userProfile", userProfileService.get(userId));
        // LINE ブロック (黒い横線) and the LINE キャラ shown on the card: 写真・緑の「LINE」・公式アカウントID
        model.addAttribute("blockedLineAccountIds", lineBlockService.blockedAccountIds(userId));
        com.crm.entity.LineAccount cardLineAccount = null;
        if (cardChara == null && !linkedCharNames.isEmpty()) {
            Long lineId = lineParam != null && linkedCharNames.containsKey(lineParam) ? lineParam : linkedCharNames.keySet().iterator().next();
            cardLineAccount = lineAccountRepository.findById(lineId).orElse(null);
        }
        model.addAttribute("cardLineAccount", cardLineAccount);
        model.addAttribute("cardLinePhoto", cardLineAccount == null ? null : lineAccountPhotoService.photoUrl(cardLineAccount.getId()));
        model.addAttribute("userPoints", userPointService.get(userId));
        model.addAttribute("memberMemo", threadPanelService.getMemo(userId, com.crm.entity.ThreadMemo.TARGET_MEMBER, 0L));
        model.addAttribute("staffMemo", threadPanelService.getMemo(userId, com.crm.entity.ThreadMemo.TARGET_STAFF, 0L));
        model.addAttribute("memoMax", com.crm.service.ThreadPanelService.MEMO_MAX);
        return "message/thread";
    }

    /** やり取りメモ (user / キャラ card) on the thread page; blank text deletes the memo. */
    @PostMapping("/manager/thread-memo")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<String> saveThreadMemo(@RequestParam String target,
                                                                          @RequestParam Long userId,
                                                                          @RequestParam(required = false) Long staffId,
                                                                          @RequestParam(required = false) String memo) {
        boolean ok = threadPanelService.saveMemo(userId, target, staffId == null ? 0L : staffId, memo);
        return ok ? org.springframework.http.ResponseEntity.ok("ok")
                  : org.springframework.http.ResponseEntity.badRequest().body("invalid memo");
    }

    @PostMapping("/manager/thread-memo/delete")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<String> deleteThreadMemo(@RequestParam String target,
                                                                            @RequestParam Long userId,
                                                                            @RequestParam(required = false) Long staffId) {
        return saveThreadMemo(target, userId, staffId, "");
    }

    /** 画面レイアウト保存: stores the 受信ボックス 4-pane sash positions (%) for the logged-in admin. */
    @PostMapping("/manager/thread-layout")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<String> saveThreadLayout(@RequestParam double wT, @RequestParam double wB,
                                                                            @RequestParam double hL, @RequestParam double hR,
                                                                            @RequestParam(defaultValue = "0") int memoMember,
                                                                            @RequestParam(defaultValue = "0") int memoStaff,
                                                                            @RequestParam(defaultValue = "1") int cards,
                                                                            HttpSession session) {
        boolean ok = threadLayoutService.save(currentAdminId(session), wT, wB, hL, hR, memoMember, memoStaff, cards != 0);
        return ok ? org.springframework.http.ResponseEntity.ok("ok")
                  : org.springframework.http.ResponseEntity.badRequest().body("invalid layout");
    }

    private static Long currentAdminId(HttpSession session) {
        Object id = session == null ? null : session.getAttribute(AuthInterceptor.SESSION_ADMIN_ID);
        return id instanceof Number ? ((Number) id).longValue() : null;
    }

    /** Submit a new outbound message for a specific user. */
    @PostMapping("/manager/users/{userId}/messages")
    public String sendMessage(@PathVariable Long userId,
                              @RequestParam(name = "returnTo", required = false) String returnTo,
                              @RequestParam(name = "charaId", required = false) Long charaId,
                              @RequestParam(name = "charaView", required = false) String charaView,
                              @Valid @ModelAttribute("form") MessageComposeForm form,
                              BindingResult br,
                              HttpSession session,
                              RedirectAttributes ra,
                              Model model) {
        if (br.hasErrors()) {
            // Flash-then-redirect (matches sendSms()/sendLine()) rather than re-rendering
            // message/thread directly — that render path needs ~15 model attributes the GET
            // handler populates (inboxRows, diffReservations, hasLineLink, etc.), and this
            // branch only ever set 5 of them, so Thymeleaf raised a raw 500 on the missing
            // ones instead of showing the validation message.
            ra.addFlashAttribute("flashError", "メール本文を入力してください");
            return "redirect:/manager/users/" + userId + redirectSuffixFor(returnTo, charaView, charaId);
        }
        Long adminId = (Long) session.getAttribute(AuthInterceptor.SESSION_ADMIN_ID);
        try {
            Message sent = messageService.compose(userId, adminId, form);
            charaLinkService.assign(com.crm.entity.CharaRef.OWNER_MESSAGE, sent.getId(), charaId);
            String kind = Message.STATUS_QUEUED.equals(sent.getStatus()) ? "予約送信" : "送信";
            ra.addFlashAttribute("flashSuccess", "メッセージを" + kind + "しました");
        } catch (MessageService.MessageException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/manager/users/" + userId + redirectSuffixFor(returnTo, charaView, charaId);
    }

    /** SMS reply from the thread page — only available for users with a registered phone number. */
    @PostMapping("/manager/users/{userId}/messages/sms")
    public String sendSms(@PathVariable Long userId,
                          @RequestParam(name = "returnTo", required = false) String returnTo,
                          @RequestParam(name = "charaId", required = false) Long charaId,
                          @RequestParam(name = "charaView", required = false) String charaView,
                          @Valid @ModelAttribute("smsForm") com.crm.dto.SmsComposeForm form,
                          BindingResult br,
                          HttpSession session,
                          RedirectAttributes ra,
                          Model model) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("flashError", "SMS本文を入力してください");
            return "redirect:/manager/users/" + userId + redirectSuffixFor(returnTo, charaView, charaId);
        }
        Long adminId = (Long) session.getAttribute(AuthInterceptor.SESSION_ADMIN_ID);
        try {
            Message sent = messageService.composeSms(userId, adminId, form);
            charaLinkService.assign(com.crm.entity.CharaRef.OWNER_MESSAGE, sent.getId(), charaId);
            String kind = Message.STATUS_QUEUED.equals(sent.getStatus()) ? "予約送信" : "送信";
            ra.addFlashAttribute("flashSuccess", "SMSを" + kind + "しました");
        } catch (MessageService.MessageException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/manager/users/" + userId + redirectSuffixFor(returnTo, charaView, charaId);
    }

    @PostMapping("/manager/users/{userId}/messages/line")
    public String sendLine(@PathVariable Long userId,
                           @RequestParam(name = "returnTo", required = false) String returnTo,
                           @RequestParam(name = "charaId", required = false) Long charaId,
                           @RequestParam(name = "charaView", required = false) String charaView,
                           @Valid @ModelAttribute("lineForm") com.crm.dto.LineComposeForm form,
                           BindingResult br,
                           HttpSession session,
                           RedirectAttributes ra,
                           Model model) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("flashError", "本文を入力してください");
            return "redirect:/manager/users/" + userId + redirectSuffixFor(returnTo, charaView, charaId);
        }
        Long adminId = (Long) session.getAttribute(AuthInterceptor.SESSION_ADMIN_ID);
        try {
            Message sent = messageService.composeLine(userId, adminId, form);
            charaLinkService.assign(com.crm.entity.CharaRef.OWNER_MESSAGE, sent.getId(), charaId);
            String kind = Message.STATUS_QUEUED.equals(sent.getStatus()) ? "予約送信" : "送信";
            auditLog.record(com.crm.service.AuditLogService.ACTION_MESSAGE_SEND, "Message", sent.getId(), "channel=LINE");
            ra.addFlashAttribute("flashSuccess", "LINEメッセージを" + kind + "しました");
        } catch (MessageService.MessageException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/manager/users/" + userId + redirectSuffixFor(returnTo, charaView, charaId);
    }

    /** メッセージボックス per-item reply forms post with returnTo=message-box so the admin
     *  lands back on the message-box preview instead of the default thread view. */
    private static String redirectSuffixFor(String returnTo) {
        return "message-box".equals(returnTo) ? "/message-box" : "/thread";
    }

    /** Same, but back on the user × キャラ page (?chara=) when the reply was sent from one. */
    private static String redirectSuffixFor(String returnTo, String charaView, Long charaId) {
        String suffix = redirectSuffixFor(returnTo);
        return "/thread".equals(suffix) && "1".equals(charaView) && charaId != null ? suffix + "?chara=" + charaId : suffix;
    }

    /**
     * Dismiss a user from the thread page's left-upper 受信 list. No password required —
     * the client confirmed this should be a one-click operation. Every IN message for the
     * user is flagged INBOX_DISMISSED_AT=NOW(); rows stay in MESSAGE so the 過去のやり取り
     * pane is unaffected. Returns 204 so the in-page JS can update the DOM without a
     * full reload (which would lose any draft reply the operator was typing).
     */
    @PostMapping("/manager/users/{userId}/inbox/dismiss")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<java.util.Map<String, Object>> dismissInbox(
            @PathVariable Long userId) {
        int n = messageService.dismissInboxForUser(userId);
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("dismissed", n);
        return org.springframework.http.ResponseEntity.ok(body);
    }

    /**
     * 予約送信 削除 from the thread view: 削除ボタン → パスワード入力 → 認証 → 削除, a single
     * password-gated step (no row-selection checkbox — that UI was the source of a bug where
     * check-then-delete silently failed). Performs the same domain-safe cancelScheduled()
     * (status -> CANCELLED, row kept for history) used elsewhere, not a hard delete, so it
     * can never race the dispatcher into deleting a row it just picked up.
     */
    @PostMapping("/manager/messages/{id}/cancel-scheduled")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<java.util.Map<String, Object>> cancelScheduledFromThread(
            @PathVariable Long id,
            @RequestParam(name = "confirmPassword", required = false) String confirmPassword,
            HttpSession session) {
        Long adminId = (Long) session.getAttribute(AuthInterceptor.SESSION_ADMIN_ID);
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        if (!adminAuthService.verifyPassword(adminId, confirmPassword)) {
            body.put("success", false);
            body.put("message", "削除には管理者パスワードの確認が必要です");
            return org.springframework.http.ResponseEntity.ok(body);
        }
        boolean ok = messageService.cancelScheduled(id);
        body.put("success", ok);
        if (!ok) body.put("message", "削除できませんでした (予約状態のみ削除可能です)");
        return org.springframework.http.ResponseEntity.ok(body);
    }

    @PostMapping("/manager/messages/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes ra) {
        if (messageService.cancelScheduled(id)) {
            ra.addFlashAttribute("flashSuccess", "予約送信をキャンセルしました");
        } else {
            ra.addFlashAttribute("flashError", "キャンセルできませんでした (予約状態のみキャンセル可能です)");
        }
        return "redirect:/manager/messages";
    }

    @GetMapping("/manager/messages/export.csv")
    public void exportCsv(@org.springframework.web.bind.annotation.RequestParam(name = "tab", required = false) String tab,
                          javax.servlet.http.HttpServletResponse response) throws java.io.IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=\"messages.csv\"; filename*=UTF-8''messages.csv");
        messageService.exportCsv(tab, response.getWriter());
        response.getWriter().flush();
    }

    @PostMapping("/manager/messages/{id}/retry")
    public String retry(@PathVariable Long id,
                        @org.springframework.web.bind.annotation.RequestParam(name = "returnTo", required = false) String returnTo,
                        RedirectAttributes ra) {
        if (messageService.retrySend(id)) {
            ra.addFlashAttribute("flashSuccess", "メッセージを再送しました");
        } else {
            ra.addFlashAttribute("flashError", "再送できませんでした (失敗またはキャンセル状態の送信メッセージのみ対象です)");
        }
        if (returnTo != null && returnTo.startsWith("/manager/")) {
            return "redirect:" + returnTo;
        }
        return "redirect:/manager/messages";
    }
}
