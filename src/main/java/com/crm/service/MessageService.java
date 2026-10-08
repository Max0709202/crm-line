package com.crm.service;

import com.crm.dto.LineComposeForm;
import com.crm.dto.MessageComposeForm;
import com.crm.entity.CarrierAddressPool;
import com.crm.entity.CrmUser;
import com.crm.entity.LineAccount;
import com.crm.entity.LineUser;
import com.crm.entity.Message;
import com.crm.repository.CarrierAddressPoolRepository;
import com.crm.repository.CrmUserRepository;
import com.crm.repository.LineAccountRepository;
import com.crm.entity.AdminUser;
import com.crm.repository.AdminUserRepository;
import com.crm.repository.LineUserRepository;
import com.crm.repository.MessageRepository;
import com.crm.util.AesEncryptionUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class MessageService {

    public static final String REPLY_URL_PLACEHOLDER = "%reply_url%";

    /**
     * Separate tag for the tracked/外部リンクドメイン URL (e.g. https://lvit4gp.jp/reply/{token}),
     * added 2026-08-23 so operators can choose explicitly between the always-reaches-the-real-
     * reply-form %reply_url% and the tracked-domain / REDIRECT / CUSTOM_HTML %external_url%,
     * instead of %reply_url% silently switching behaviour based on whichever domain happens to
     * be 使用中. Both tags point at the SAME underlying ReplyPage/token when both are present in
     * one body — only the domain differs. Substituted with an empty string when no 外部リンク
     * ドメイン is currently active.
     */
    public static final String EXTERNAL_URL_PLACEHOLDER = "%external_url%";

    /** Default clip length, used only as a fallback when no channel-specific setting resolves
     *  (see DomainSettingService#getEmailReplyUrlClipLength / SmsSettingService#getReplyUrlClipLength,
     *  now operator-configurable per channel). */
    public static final int REPLY_URL_CLIP_LENGTH = 15;

    private final MessageRepository messageRepository;
    private final CrmUserRepository userRepository;
    private final CarrierAddressPoolRepository poolRepository;
    private final CarrierBindingService bindingService;
    private final PlaceholderService placeholderService;
    private final OutboundMailService outboundMailService;
    private final OutboundSmsService outboundSmsService;
    private final SmsSettingService smsSettingService;
    private final AesEncryptionUtil aes;
    private final ReplyPageService replyPageService;
    private final DomainSettingService domainSettingService;
    private final ReplyPageSettingService replyPageSettingService;
    private final OutboundLineService outboundLineService;
    private final LineAccountRepository lineAccountRepository;
    private final LineUserRepository lineUserRepository;
    private final AdminUserRepository adminUserRepository;
    /** Lazy reference — broadcast counter update is optional and avoids a circular dependency. */
    private final org.springframework.context.ApplicationContext ctx;

    /** 紐づきキャラ — optional so hand-built instances (tests) work without it. */
    private CharaLinkService charaLinkService;

    /** LINE送信テキスト (最大文字数・固定テンプレート) — optional so hand-built instances (tests) work without it. */
    private LineTextService lineTextService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setLineTextService(LineTextService lineTextService) { this.lineTextService = lineTextService; }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setCharaLinkService(CharaLinkService charaLinkService) { this.charaLinkService = charaLinkService; }

    /** 画像添付 / LINE画像挿入 — optional (tests). */
    private MessageImageService messageImageService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setMessageImageService(MessageImageService messageImageService) { this.messageImageService = messageImageService; }

    /** メールテンプレート設定 › メール通知 as the form of a キャラ mail — optional (tests). */
    private MailMessageTemplateService mailMessageTemplateService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setMailMessageTemplateService(MailMessageTemplateService s) { this.mailMessageTemplateService = s; }

    /** サポート窓口's 送信者名 — the From name of a キャラ指定なし mail. Optional (tests). */
    private SupportDeskService supportDeskService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSupportDeskService(SupportDeskService supportDeskService) { this.supportDeskService = supportDeskService; }

    /**
     * キャラ指定なし (🎧 サポート窓口) の一斉送信・差分ステップ mail: From's display name is サポート窓口's
     * 送信者名 ("サポート窓口" while unset), so it arrives from サポート窓口, not a キャラ / 運営 name.
     * null for every other mail (the sender-name policy applies as before).
     */
    private String supportDisplayName(Message msg) {
        if (supportDeskService == null || charaLinkService == null || msg.getBroadcastId() == null) return null;
        if (charaLinkService.charaIdOfMessage(msg) != null) return null;
        String name = supportDeskService.senderName();
        return name.isEmpty() ? MemberSiteService.SUPPORT_NAME : name;
    }

    public MessageService(MessageRepository messageRepository,
                          CrmUserRepository userRepository,
                          CarrierAddressPoolRepository poolRepository,
                          CarrierBindingService bindingService,
                          PlaceholderService placeholderService,
                          OutboundMailService outboundMailService,
                          OutboundSmsService outboundSmsService,
                          SmsSettingService smsSettingService,
                          AesEncryptionUtil aes,
                          ReplyPageService replyPageService,
                          DomainSettingService domainSettingService,
                          ReplyPageSettingService replyPageSettingService,
                          OutboundLineService outboundLineService,
                          LineAccountRepository lineAccountRepository,
                          LineUserRepository lineUserRepository,
                          AdminUserRepository adminUserRepository,
                          org.springframework.context.ApplicationContext ctx) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.poolRepository = poolRepository;
        this.bindingService = bindingService;
        this.placeholderService = placeholderService;
        this.outboundMailService = outboundMailService;
        this.outboundSmsService = outboundSmsService;
        this.smsSettingService = smsSettingService;
        this.aes = aes;
        this.replyPageService = replyPageService;
        this.domainSettingService = domainSettingService;
        this.replyPageSettingService = replyPageSettingService;
        this.outboundLineService = outboundLineService;
        this.lineAccountRepository = lineAccountRepository;
        this.lineUserRepository = lineUserRepository;
        this.adminUserRepository = adminUserRepository;
        this.ctx = ctx;
    }

    /**
     * Thread display for the user-detail pane. Future-scheduled outbound messages
     * (status=QUEUED with scheduledAt > now) are pinned to the top in scheduled-time
     * DESC order, so an operator sees "what's about to fire" before the historical
     * sent/received trail. Everything else falls below, sorted by createdAt DESC.
     * Operator request 2026-05-23.
     */
    public List<Message> threadFor(Long userId) {
        List<Message> all = messageRepository.findByUserIdOrderByCreatedAtDesc(userId);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        List<Message> future = new java.util.ArrayList<>();
        List<Message> rest = new java.util.ArrayList<>();
        for (Message m : all) {
            if (Message.STATUS_QUEUED.equals(m.getStatus())
                    && m.getScheduledAt() != null && m.getScheduledAt().isAfter(now)) {
                future.add(m);
            } else {
                rest.add(m);
            }
        }
        future.sort((a, b) -> b.getScheduledAt().compareTo(a.getScheduledAt()));
        List<Message> out = new java.util.ArrayList<>(all.size());
        out.addAll(future);
        out.addAll(rest);
        return out;
    }

    /** One row per user that has inbound messages, newest first. For /manager/inbox. */
    public List<InboxRow> inboxByUser(boolean unreadOnly) {
        List<Object[]> groups = messageRepository.inboxGroupByUser();
        if (groups.isEmpty()) return java.util.Collections.emptyList();

        // Index userIds, latestIds; skip users with zero unread when filter is on
        java.util.List<Long> userIds = new java.util.ArrayList<>();
        java.util.List<Long> latestIds = new java.util.ArrayList<>();
        java.util.List<Object[]> kept = new java.util.ArrayList<>();
        for (Object[] g : groups) {
            long unread = ((Number) g[2]).longValue();
            if (unreadOnly && unread == 0L) continue;
            kept.add(g);
            userIds.add(((Number) g[0]).longValue());
            latestIds.add(((Number) g[1]).longValue());
        }
        if (kept.isEmpty()) return java.util.Collections.emptyList();

        java.util.Map<Long, CrmUser> userById = new java.util.HashMap<>();
        for (CrmUser u : userRepository.findAllById(userIds)) userById.put(u.getId(), u);

        java.util.Map<Long, Message> msgById = new java.util.HashMap<>();
        for (Message m : messageRepository.findAllById(latestIds)) msgById.put(m.getId(), m);

        // キャラ名 column: the LINE account (character) the latest inbound message came in on.
        java.util.Set<Long> lineAccountIds = new java.util.HashSet<>();
        for (Message m : msgById.values()) if (m.getLineAccountId() != null) lineAccountIds.add(m.getLineAccountId());
        java.util.Map<Long, String> lineAccountNames = new java.util.HashMap<>();
        if (!lineAccountIds.isEmpty()) {
            for (com.crm.entity.LineAccount a : lineAccountRepository.findAllById(lineAccountIds)) {
                lineAccountNames.put(a.getId(), a.getName());
            }
        }

        java.util.List<InboxRow> out = new java.util.ArrayList<>(kept.size());
        for (Object[] g : kept) {
            InboxRow r = new InboxRow();
            r.userId = ((Number) g[0]).longValue();
            r.unreadCount = ((Number) g[2]).longValue();
            r.webReplyCount = ((Number) g[3]).longValue();
            r.mailReplyCount = ((Number) g[4]).longValue();
            r.outCount = ((Number) g[5]).longValue();
            // 未返信 = there is at least one non-dismissed IN row and no OUT row arrived after it.
            // g[6]/g[7] are java.sql.Timestamp from the native query; coerce safely.
            java.time.LocalDateTime latestIn = toLdt(g[6]);
            java.time.LocalDateTime latestOut = toLdt(g[7]);
            r.unreplied = (latestIn != null) && (latestOut == null || latestOut.isBefore(latestIn));
            r.smsOutCount = ((Number) g[8]).longValue();
            CrmUser u = userById.get(r.userId);
            if (u != null) {
                r.displayName = (u.getDisplayName() == null || u.getDisplayName().isEmpty())
                        ? u.getEmail() : u.getDisplayName();
                r.email = u.getEmail();
                r.phoneNumber = u.getPhoneNumber();
            } else {
                r.displayName = "ID=" + r.userId;
                r.email = "";
            }
            Message m = msgById.get(((Number) g[1]).longValue());
            if (m != null) {
                r.latestSubject = m.getSubject();
                r.latestPreview = preview(m.getBodyText(), 80);
                r.latestBody = m.getBodyText() == null ? "" : m.getBodyText().trim();
                r.latestChannel = m.getChannel();
                r.latestLineAccountName = m.getLineAccountId() == null ? null : lineAccountNames.get(m.getLineAccountId());
                r.latestAt = m.getCreatedAt();
                r.latestMessageId = m.getId();
                r.latestRead = m.getReadAt() != null;
            }
            out.add(r);
        }
        return out;
    }

    private static String preview(String body, int max) {
        if (body == null) return "";
        String s = body.replaceAll("\\s+", " ").trim();
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** Coerce native-query MAX(CREATED_AT) result (Timestamp or LocalDateTime depending on driver) to LocalDateTime. */
    private static java.time.LocalDateTime toLdt(Object v) {
        if (v == null) return null;
        if (v instanceof java.time.LocalDateTime) return (java.time.LocalDateTime) v;
        if (v instanceof java.sql.Timestamp) return ((java.sql.Timestamp) v).toLocalDateTime();
        return null;
    }

    /**
     * Dismiss every non-dismissed inbound message for {@code userId}. The user disappears
     * from the left-upper 受信 list on thread.html; Message rows are NOT deleted so the
     * 過去のやり取り pane and per-user thread history stay intact. Used by the per-row × button.
     */
    @Transactional
    public int dismissInboxForUser(Long userId) {
        return messageRepository.dismissInboxByUserId(userId, LocalDateTime.now());
    }

    /** Flat row for the inbox triage list. */
    public static class InboxRow {
        public Long userId;
        public String displayName;
        public String email;
        public String phoneNumber;
        public long unreadCount;
        public long webReplyCount;
        public long mailReplyCount;
        public long outCount;
        public long smsOutCount;
        public Long latestMessageId;
        public String latestSubject;
        public String latestPreview;
        /** Full body of the latest inbound message (the 最新メッセージ column shows it untruncated). */
        public String latestBody;
        /** CHANNEL of the latest inbound message — drives the 種別 badge. */
        public String latestChannel;
        /** LINE account (キャラ) name of the latest inbound message; null for non-LINE. */
        public String latestLineAccountName;
        public LocalDateTime latestAt;
        public boolean latestRead;
        /** true when the user has at least one IN message and we haven't sent any OUT after it. */
        public boolean unreplied;

        public Long getUserId() { return userId; }
        public String getDisplayName() { return displayName; }
        public String getEmail() { return email; }
        public String getPhoneNumber() { return phoneNumber; }
        public long getUnreadCount() { return unreadCount; }
        public long getWebReplyCount() { return webReplyCount; }
        public long getMailReplyCount() { return mailReplyCount; }
        public long getOutCount() { return outCount; }
        public long getSmsOutCount() { return smsOutCount; }
        public Long getLatestMessageId() { return latestMessageId; }
        public String getLatestSubject() { return latestSubject; }
        public String getLatestPreview() { return latestPreview; }
        public String getLatestBody() { return latestBody; }
        public String getLatestChannel() { return latestChannel; }
        public String getLatestLineAccountName() { return latestLineAccountName; }
        public LocalDateTime getLatestAt() { return latestAt; }
        public boolean isLatestRead() { return latestRead; }
        public boolean isUnreplied() { return unreplied; }
    }

    /** Mark all inbound messages for this user as read. Called when admin opens the thread. */
    @Transactional
    public int markThreadAsRead(Long userId) {
        return messageRepository.markReadByUserAndDirection(userId, Message.DIR_IN, LocalDateTime.now());
    }

    /**
     * Same as {@link #markThreadAsRead} but for many users at once — called when an automated
     * send (LINE auto-reply, diff-schedule broadcast) is itself what handles a customer's prior
     * inbound, at the moment that send actually happens, rather than waiting for an admin to
     * later open the thread manually (2026-09-23 client request).
     */
    @Transactional
    public int markThreadsAsRead(java.util.Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return 0;
        return messageRepository.markReadByUsersAndDirection(userIds, Message.DIR_IN, LocalDateTime.now());
    }

    /**
     * Predicate excluding broadcast-related messages from /manager/messages.
     *   \u2022 OUT dispatched by a broadcast (broadcastId NOT NULL) \u2014 handled by /broadcast page
     *   \u2022 IN replying to such an OUT (replyToMessageId points to a broadcast OUT) \u2014 same
     *
     * Inverse of {@code MessageRepository.findBroadcastRelated}, used as a runtime filter so
     * the two list pages have non-overlapping content without a DB migration.
     */
    private static javax.persistence.criteria.Predicate notBroadcastRelated(
            javax.persistence.criteria.Root<Message> root,
            javax.persistence.criteria.AbstractQuery<?> q,
            javax.persistence.criteria.CriteriaBuilder cb) {
        javax.persistence.criteria.Subquery<Long> broadcastOutIds = q.subquery(Long.class);
        javax.persistence.criteria.Root<Message> bRoot = broadcastOutIds.from(Message.class);
        broadcastOutIds.select(bRoot.get("id"));
        broadcastOutIds.where(cb.isNotNull(bRoot.get("broadcastId")));
        return cb.and(
                cb.isNull(root.get("broadcastId")),
                cb.or(
                        cb.isNull(root.get("replyToMessageId")),
                        cb.not(root.get("replyToMessageId").in(broadcastOutIds))
                )
        );
    }

    /** CSV export for the messages list, optionally filtered by tab. UTF-8 BOM for Excel. */
    public void exportCsv(String tab, java.io.Writer writer) throws java.io.IOException {
        writer.write('\uFEFF');
        try (com.opencsv.CSVWriter csv = new com.opencsv.CSVWriter(writer)) {
            csv.writeNext(new String[]{
                    "id", "direction", "channel", "status",
                    "user_id", "to_address", "from_address",
                    "subject", "body_text", "scheduled_at", "sent_at", "created_at"});
            org.springframework.data.jpa.domain.Specification<Message> spec = (root, q, cb) -> {
                java.util.List<javax.persistence.criteria.Predicate> preds = new java.util.ArrayList<>();
                preds.add(notBroadcastRelated(root, q, cb));
                if ("sent".equals(tab)) {
                    preds.add(cb.equal(root.get("direction"), Message.DIR_OUT));
                    preds.add(cb.isNull(root.get("replyToMessageId")));
                    preds.add(root.get("status").in(Message.STATUS_SENT, Message.STATUS_DELIVERED, Message.STATUS_FAILED));
                } else if ("scheduled".equals(tab)) {
                    preds.add(cb.equal(root.get("direction"), Message.DIR_OUT));
                    preds.add(cb.isNull(root.get("replyToMessageId")));
                    preds.add(root.get("status").in(Message.STATUS_QUEUED, Message.STATUS_CANCELLED));
                } else if ("reply".equals(tab)) {
                    preds.add(cb.equal(root.get("direction"), Message.DIR_OUT));
                    preds.add(cb.isNotNull(root.get("replyToMessageId")));
                    preds.add(root.get("status").in(Message.STATUS_SENT, Message.STATUS_DELIVERED, Message.STATUS_FAILED));
                } else if ("scheduled-reply".equals(tab)) {
                    preds.add(cb.equal(root.get("direction"), Message.DIR_OUT));
                    preds.add(cb.isNotNull(root.get("replyToMessageId")));
                    preds.add(root.get("status").in(Message.STATUS_QUEUED, Message.STATUS_CANCELLED));
                } else if ("inbound".equals(tab)) {
                    preds.add(cb.equal(root.get("direction"), Message.DIR_IN));
                }
                return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new javax.persistence.criteria.Predicate[0]));
            };
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            for (Message m : messageRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"))) {
                csv.writeNext(new String[]{
                        String.valueOf(m.getId()),
                        s(m.getDirection()), s(m.getChannel()), s(m.getStatus()),
                        String.valueOf(m.getUserId()),
                        s(m.getToAddress()), s(m.getFromAddress()),
                        s(m.getSubject()), s(m.getBodyText()),
                        m.getScheduledAt() == null ? "" : m.getScheduledAt().format(fmt),
                        m.getSentAt() == null ? "" : m.getSentAt().format(fmt),
                        m.getCreatedAt() == null ? "" : m.getCreatedAt().format(fmt)
                });
            }
        }
    }
    private static String s(String v) { return v == null ? "" : v; }

    public Page<Message> recentMessages(int page, int size) {
        return recentMessages(page, size, null);
    }

    @org.springframework.transaction.annotation.Transactional
    public int deleteByIds(java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        // 紐づきキャラ: a deleted message from the user unlinks the キャラ it was sent to
        if (charaLinkService != null) {
            java.util.List<Long> existing = new java.util.ArrayList<>();
            for (Long id : ids) if (id != null) existing.add(id);
            charaLinkService.onMessagesDeleted(messageRepository.findAllById(existing));
        }
        int n = 0;
        for (Long id : ids) {
            if (id == null) continue;
            try { messageRepository.deleteById(id); n++; } catch (Exception ignored) {}
        }
        return n;
    }

    /**
     * Queue per-user reply messages for each selected user. Subject/body are passed
     * through placeholder substitution per recipient (%name%, %email%, %amount%, …).
     * Skips users with no carrier-pool binding or no active pool. Returns the count
     * actually queued for dispatch.
     */
    @org.springframework.transaction.annotation.Transactional
    public int bulkReplyToUsers(java.util.List<Long> userIds, String subject, String body) {
        if (userIds == null || userIds.isEmpty()) return 0;
        if (body == null) body = "";
        if (subject == null) subject = "";

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int queued = 0;
        for (Long uid : userIds) {
            if (uid == null) continue;
            java.util.Optional<CrmUser> uOpt = userRepository.findById(uid);
            if (!uOpt.isPresent()) continue;
            CrmUser user = uOpt.get();
            if (user.getEmail() == null || user.getEmail().isEmpty()) continue; // SMS-only user

            // 2026-05-24: fall back to base-domain FROM when no active pool binding so
            // operators can still bulk-reply to users they haven't carrier-bound yet.
            java.util.Optional<CarrierAddressPool> poolOpt = bindingService.firstBoundFor(uid);
            CarrierAddressPool pool = (poolOpt.isPresent()
                    && !Boolean.FALSE.equals(poolOpt.get().getIsActive())) ? poolOpt.get() : null;
            String fromAddr = (pool != null) ? pool.getAddress() : domainSettingService.buildFromAddress();
            if (fromAddr == null || fromAddr.isEmpty()) continue;

            String renderedSubject = placeholderService.substitute(subject, user);
            String renderedBody    = placeholderService.substitute(body, user);

            Message m = new Message();
            m.setUserId(uid);
            m.setDirection(Message.DIR_OUT);
            m.setChannel(Message.CHANNEL_EMAIL);
            m.setSubject(renderedSubject);
            m.setBodyText(renderedBody);
            m.setFromAddress(fromAddr);
            m.setToAddress(user.getEmail());
            m.setStatus(Message.STATUS_QUEUED);
            m.setScheduledAt(now);
            messageRepository.save(m);
            queued++;
        }
        return queued;
    }

    /** Delete all inbound (DIR_IN) messages for the given users. Used by 受信管理 bulk delete. */
    @org.springframework.transaction.annotation.Transactional
    public int deleteInboundForUsers(java.util.List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return 0;
        int total = 0;
        for (Long uid : userIds) {
            if (uid == null) continue;
            for (Message m : messageRepository.findByUserIdOrderByCreatedAtAsc(uid)) {
                if (Message.DIR_IN.equals(m.getDirection())) {
                    if (charaLinkService != null) charaLinkService.onMessagesDeleted(java.util.Collections.singletonList(m));
                    try { messageRepository.delete(m); total++; } catch (Exception ignored) {}
                }
            }
        }
        return total;
    }

    /**
     * Tab filters for /manager/messages:
     *   "sent"           — new outbound that has been dispatched (not a reply)
     *   "scheduled"      — new outbound awaiting scheduler (not a reply)
     *   "reply"          — admin replies that have been dispatched
     *   "scheduled-reply"— admin replies awaiting scheduler
     *   "inbound"        — replies from users (DIRECTION=IN)
     *   null/other       — everything
     */
    public Page<Message> recentMessages(int page, int size, String tab) {
        return recentMessages(page, size, tab, null);
    }

    /**
     * @param lineAccountId when non-null, scopes to that account's LINE traffic only — this is
     *                      an account-wide "やり取り履歴" (used by the LINE settings screen's
     *                      per-account 履歴 link), so unlike the default view it deliberately
     *                      does NOT exclude broadcast-related rows: a broadcast sent from this
     *                      account is still part of "the exchange history through this account".
     *                      {@code tab} is ignored when this is set.
     */
    public Page<Message> recentMessages(int page, int size, String tab, Long lineAccountId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (lineAccountId != null) {
            org.springframework.data.jpa.domain.Specification<Message> byAccount = (root, q, cb) ->
                    cb.and(cb.equal(root.get("channel"), Message.CHANNEL_LINE),
                           cb.equal(root.get("lineAccountId"), lineAccountId));
            return messageRepository.findAll(byAccount, pageable);
        }
        org.springframework.data.jpa.domain.Specification<Message> spec = (root, q, cb) -> {
            java.util.List<javax.persistence.criteria.Predicate> preds = new java.util.ArrayList<>();
            preds.add(notBroadcastRelated(root, q, cb));
            if ("sent".equals(tab)) {
                preds.add(cb.equal(root.get("direction"), Message.DIR_OUT));
                preds.add(cb.isNull(root.get("replyToMessageId")));
                preds.add(root.get("status").in(
                        Message.STATUS_SENT, Message.STATUS_DELIVERED, Message.STATUS_FAILED));
            } else if ("scheduled".equals(tab)) {
                preds.add(cb.equal(root.get("direction"), Message.DIR_OUT));
                preds.add(cb.isNull(root.get("replyToMessageId")));
                preds.add(root.get("status").in(
                        Message.STATUS_QUEUED, Message.STATUS_CANCELLED));
            } else if ("inbound".equals(tab)) {
                preds.add(cb.equal(root.get("direction"), Message.DIR_IN));
            }
            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new javax.persistence.criteria.Predicate[0]));
        };
        return messageRepository.findAll(spec, pageable);
    }

    /** The day the user receives a send — its 予約 day when scheduled ahead, else today (%date_jp% etc.). */
    private static java.time.LocalDate receivedOn(LocalDateTime scheduledAt) {
        return scheduledAt != null && scheduledAt.isAfter(LocalDateTime.now()) ? scheduledAt.toLocalDate() : java.time.LocalDate.now();
    }

    /**
     * Send immediately or queue a scheduled send. Subject + body are substituted
     * against the user's placeholder bindings before storage/sending.
     */
    @Transactional
    public Message compose(Long userId, Long adminUserId, MessageComposeForm form) {
        CrmUser user = userRepository.findById(userId)
                .orElseThrow(() -> new MessageException("ユーザーが見つかりません"));
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            throw new MessageException("このユーザーはメールアドレスが未登録です（電話番号のみ登録）。SMS送信をご利用ください");
        }
        // 2026-05-24: unbound users used to be rejected here, but the operator can still
        // send via the base-domain FROM (from.base_domain setting) — the reply-page URL in
        // the body handles round-tripping. Fall back when no active pool binding exists.
        CarrierAddressPool pool = bindingService.firstBoundFor(userId).orElse(null);
        if (pool != null && Boolean.FALSE.equals(pool.getIsActive())) pool = null;
        String fromAddr = (pool != null) ? pool.getAddress() : domainSettingService.buildFromAddress();
        if (fromAddr == null || fromAddr.isEmpty()) {
            throw new MessageException("送信元アドレスが解決できません (キャリア未割当かつ from.base_domain 未設定)");
        }

        java.time.LocalDate receivedOn = receivedOn(form.getScheduledAt());
        String renderedSubject = placeholderService.substitute(form.getSubject(), user, receivedOn);
        List<Long> imageIds = validImages(form.getImageIds(), false);
        String renderedBody = withReplyUrlForImages(placeholderService.substitute(form.getBody(), user, receivedOn), imageIds);

        Message msg = new Message();
        msg.setUserId(userId);
        msg.setAdminUserId(adminUserId);
        msg.setDirection(Message.DIR_OUT);
        msg.setChannel(Message.CHANNEL_EMAIL);
        msg.setSubject(renderedSubject);
        msg.setBodyText(renderedBody);
        msg.setFromAddress(fromAddr);
        msg.setToAddress(user.getEmail());
        msg.setReplyToMessageId(form.getReplyToMessageId());

        // Persist first so we have an ID for the reply-page binding if needed.
        boolean needsAnyUrl = renderedBody != null
                && (renderedBody.contains(REPLY_URL_PLACEHOLDER) || renderedBody.contains(EXTERNAL_URL_PLACEHOLDER));
        if (mailMessageTemplateService != null && mailMessageTemplateService.isActive()) {
            // メール通知 (with %body%) is the form of the mail: the 文字数設定 counts only %body%
            msg.setStatus(Message.STATUS_DRAFT);
            msg = messageRepository.save(msg);
            mailMessageTemplateService.apply(msg, user, renderedSubject, renderedBody,
                    charaLinkService == null ? null : charaLinkService.charaName(form.getCharaId()));
        } else if (needsAnyUrl) {
            // Temporary body/status so we can save; we'll rewrite after the page is created.
            msg.setStatus(Message.STATUS_DRAFT);
            msg = messageRepository.save(msg);
            applyUrlPlaceholders(msg, renderedBody, replyPageService.createReplyPageFor(msg), domainSettingService,
                    domainSettingService.getEmailReplyUrlClipLength());
            // Historical/audit record only — records what the domain's landing mode was AT
            // SEND TIME. メッセージボックス no longer reads this column to decide visibility.
            msg.setExcludedFromBox(domainSettingService.isActiveLinkDomainExternalLanding());
            // fall through — subsequent save() / sendNow() path will update this row
        }

        LocalDateTime scheduled = form.getScheduledAt();
        LocalDateTime now = LocalDateTime.now();

        if (scheduled != null && scheduled.isAfter(now)) {
            msg.setStatus(Message.STATUS_QUEUED);
            msg.setScheduledAt(scheduled);
            return attachImages(messageRepository.save(msg), imageIds);
        }

        // Immediate send
        msg.setStatus(Message.STATUS_QUEUED); // transient; updated below
        Message saved = attachImages(messageRepository.save(msg), imageIds);
        sendNow(saved, pool);
        return saved;
    }

    /**
     * SMS reply from the thread page — deliberately separate from {@link #compose}, which
     * resolves a carrier-pool FROM address and has no meaning for the SMS channel. Only
     * available when the user has a registered phone number.
     */
    @Transactional
    public Message composeSms(Long userId, Long adminUserId, com.crm.dto.SmsComposeForm form) {
        CrmUser user = userRepository.findById(userId)
                .orElseThrow(() -> new MessageException("ユーザーが見つかりません"));
        String phone = user.getPhoneNumber();
        if (phone == null || phone.trim().isEmpty()) {
            throw new MessageException("このユーザーには電話番号が登録されていません");
        }

        List<Long> imageIds = validImages(form.getImageIds(), false);
        String renderedBody = withReplyUrlForImages(placeholderService.substitute(form.getBody(), user, receivedOn(form.getScheduledAt())), imageIds);

        Message msg = new Message();
        msg.setUserId(userId);
        msg.setAdminUserId(adminUserId);
        msg.setDirection(Message.DIR_OUT);
        msg.setChannel(Message.CHANNEL_SMS);
        msg.setBodyText(renderedBody);
        msg.setFromAddress(smsSettingService.resolveSenderName());
        msg.setToAddress(phone);
        msg.setReplyToMessageId(form.getReplyToMessageId());

        // Same %reply_url% / %external_url% handling as compose() — the tag panel is shared
        // between the email and SMS reply forms, but this substitution was missing here, so an
        // SMS reply containing %reply_url% went out with the literal placeholder text intact.
        // Uses the short (10-char) token, not the 64-char email one — SMS is billed per
        // ~65-char segment, so the long form alone would consume the whole budget.
        boolean needsAnyUrl = renderedBody != null
                && (renderedBody.contains(REPLY_URL_PLACEHOLDER) || renderedBody.contains(EXTERNAL_URL_PLACEHOLDER));
        if (needsAnyUrl) {
            msg.setStatus(Message.STATUS_DRAFT);
            msg = messageRepository.save(msg);
            applyUrlPlaceholders(msg, renderedBody, replyPageService.createShortReplyPageFor(msg), domainSettingService,
                    smsSettingService.getReplyUrlClipLength());
            // Historical/audit record only — see the matching comment in compose() above.
            msg.setExcludedFromBox(domainSettingService.isActiveLinkDomainExternalLanding());
        }

        LocalDateTime scheduled = form.getScheduledAt();
        LocalDateTime now = LocalDateTime.now();
        if (scheduled != null && scheduled.isAfter(now)) {
            msg.setStatus(Message.STATUS_QUEUED);
            msg.setScheduledAt(scheduled);
            return attachImages(messageRepository.save(msg), imageIds);
        }

        msg.setStatus(Message.STATUS_QUEUED); // transient; updated below
        Message saved = attachImages(messageRepository.save(msg), imageIds);
        sendNow(saved, null);
        return saved;
    }

    /** LINE messages are capped at 5000 chars by LINE itself; this leaves headroom for the
     *  lead-text/URL suffix appended after clipping. Unlike SMS there's no per-segment
     *  billing reason to clip much shorter, so this is a safety cap, not a real limit. */
    static final int LINE_REPLY_URL_CLIP_LENGTH = 4900;

    /**
     * LINE reply from the thread page — mirrors {@link #composeSms}. Only available once
     * the customer has a linked {@link LineUser} row (LINE only lets you message someone
     * who has already followed the Official Account and triggered a webhook event — there's
     * no "type in an id and message them" the way email/SMS work).
     */
    @Transactional
    public Message composeLine(Long userId, Long adminUserId, LineComposeForm form) {
        return composeLine(userId, adminUserId, form, true);
    }

    /**
     * @param lineTextRules false for LINE 自動応答: its reply text is sent as written — the
     *                      LINE設定 max-length cut and 固定テンプレート apply to 個別返信・一斉送信・差分ステップ
     */
    @Transactional
    public Message composeLine(Long userId, Long adminUserId, LineComposeForm form, boolean lineTextRules) {
        CrmUser user = userRepository.findById(userId)
                .orElseThrow(() -> new MessageException("ユーザーが見つかりません"));
        // Most recently messaged first — the default sender when no character is chosen.
        List<LineUser> linked = lineUserRepository.findByCrmUserIdInOrderByLastMessageAtDesc(
                java.util.Collections.singletonList(userId));
        if (linked.isEmpty()) {
            throw new MessageException("このユーザーはLINEと連携されていません");
        }
        LineUser lineUser = linked.get(0);
        if (form.getLineAccountId() != null) {
            lineUser = linked.stream()
                    .filter(lu -> form.getLineAccountId().equals(lu.getLineAccountId()))
                    .findFirst()
                    .orElseThrow(() -> new MessageException("選択したキャラ（LINEアカウント）とこのユーザーは友だちではありません"));
        }

        String renderedBody = placeholderService.substitute(form.getBody(), user, receivedOn(form.getScheduledAt()));
        // LINE画像挿入: sent to LINE as images after the text (not counted in the 最大文字数)
        List<Long> imageIds = validImages(form.getImageIds(), true);

        Message msg = new Message();
        msg.setUserId(userId);
        msg.setAdminUserId(adminUserId);
        msg.setDirection(Message.DIR_OUT);
        msg.setChannel(Message.CHANNEL_LINE);
        msg.setLineAccountId(lineUser.getLineAccountId());
        msg.setBodyText(renderedBody);
        msg.setToAddress(lineUser.getLineUserId());
        msg.setReplyToMessageId(form.getReplyToMessageId());

        boolean needsAnyUrl = renderedBody != null
                && (renderedBody.contains(REPLY_URL_PLACEHOLDER) || renderedBody.contains(EXTERNAL_URL_PLACEHOLDER));
        if (lineTextRules && lineTextService != null) {
            // LINE設定: 最大文字数を超えた分は返信URL先へ・固定テンプレート・短縮URL (LineTextService)
            msg.setStatus(Message.STATUS_DRAFT);
            msg = messageRepository.save(msg);
            lineTextService.apply(msg, user, renderedBody);
        } else if (needsAnyUrl) {
            msg.setStatus(Message.STATUS_DRAFT);
            msg = messageRepository.save(msg);
            // Short token (matches composeSms()'s createShortReplyPageFor()) — LINE bans/flags
            // accounts for long messages, and the CRM's own domain reply URL was previously
            // using the 64-char email-style token, making it needlessly long. Both token
            // lengths serve the exact same /reply/{token} page on this CRM's own domain; there
            // is no separate relay/short-link server involved for any channel.
            applyUrlPlaceholders(msg, renderedBody, replyPageService.createShortReplyPageFor(msg), domainSettingService,
                    LINE_REPLY_URL_CLIP_LENGTH);
            msg.setExcludedFromBox(domainSettingService.isActiveLinkDomainExternalLanding());
        }

        LocalDateTime scheduled = form.getScheduledAt();
        LocalDateTime now = LocalDateTime.now();
        if (scheduled != null && scheduled.isAfter(now)) {
            msg.setStatus(Message.STATUS_QUEUED);
            msg.setScheduledAt(scheduled);
            return attachImages(messageRepository.save(msg), imageIds);
        }

        msg.setStatus(Message.STATUS_QUEUED); // transient; updated below
        Message saved = attachImages(messageRepository.save(msg), imageIds);
        sendNow(saved, null);
        return saved;
    }

    /** 画像添付 / LINE画像挿入: the chosen HTML画像 ids, checked (MessageException on a bad choice). */
    private List<Long> validImages(List<Long> ids, boolean forLine) {
        if (ids == null || ids.isEmpty()) return java.util.Collections.emptyList();
        if (messageImageService == null) return java.util.Collections.emptyList();
        try {
            return messageImageService.validIds(ids, forLine);
        } catch (MessageImageService.ImageException e) {
            throw new MessageException(e.getMessage());
        }
    }

    /**
     * メール / SMS with 画像添付: the images are shown on the 返信画面 (not as a URL in the mail), so a
     * mail without %reply_url% gets one at the end — otherwise the member could never reach them.
     */
    static String withReplyUrlForImages(String body, List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) return body;
        String b = body == null ? "" : body;
        if (b.contains(REPLY_URL_PLACEHOLDER)) return b;
        return b.endsWith("\n") || b.isEmpty() ? b + REPLY_URL_PLACEHOLDER : b + "\n" + REPLY_URL_PLACEHOLDER;
    }

    private Message attachImages(Message saved, List<Long> imageIds) {
        if (messageImageService != null && imageIds != null && !imageIds.isEmpty()) {
            messageImageService.attach(com.crm.entity.MessageImage.OWNER_MESSAGE, saved.getId(), imageIds);
        }
        return saved;
    }

    /**
     * Dispatch a saved outbound message via the relay. Called on immediate send
     * and (future) by the scheduler for QUEUED entries whose scheduled time has arrived.
     *
     * {@code pool} is now informational — kept for legacy callers but not required.
     * Outbound transport is decided entirely by the active RELAY_SERVER row in
     * {@link HttpRelayOutboundMailService}, so smtpHost/Port/User/Password from the
     * pool are no longer used. Pass {@code null} when the message has no associated pool
     * (the carrier-pool became receive-only as of the 2026-05 dispatcher refactor).
     */
    @Transactional
    public void sendNow(Message msg, CarrierAddressPool pool) {
        boolean success;
        boolean retriable;
        String errorMessage;

        // SENT_BODY_TEXT holds the 15-char-clipped text when %reply_url% was present at compose
        // time (see clipForTransmission()); BODY_TEXT is always the full text for メッセージボックス.
        // Falls back to BODY_TEXT when SENT_BODY_TEXT was never set — unchanged behaviour for the
        // no-%reply_url% case. Works for both immediate send and scheduler-dispatched QUEUED rows,
        // since both columns are persisted together at compose/queue time.
        String transmitBody = msg.getSentBodyText() != null ? msg.getSentBodyText() : msg.getBodyText();

        if (Message.CHANNEL_SMS.equals(msg.getChannel())) {
            // Use the sender name already resolved and stored on the row at compose/queue time
            // (msg.getFromAddress()) — NOT smsSettingService.resolveSenderName() again here.
            // With a random sender-name mode, calling resolve() a second time would send BytePlus
            // a different name than the one recorded in our own history.
            OutboundSmsService.SmsSendRequest req = new OutboundSmsService.SmsSendRequest(
                    smsSettingService.getUsername(),
                    smsSettingService.getPassword(),
                    msg.getFromAddress(),
                    msg.getToAddress(),
                    transmitBody == null ? "" : transmitBody);
            OutboundSmsService.SendResult result = outboundSmsService.send(req);
            success = result.success;
            retriable = result.retriable;
            errorMessage = result.errorMessage;
        } else if (Message.CHANNEL_LINE.equals(msg.getChannel())) {
            LineAccount account = msg.getLineAccountId() == null ? null
                    : lineAccountRepository.findById(msg.getLineAccountId()).orElse(null);
            if (account == null) {
                success = false;
                retriable = false;
                errorMessage = "LINEアカウントが見つかりません (id=" + msg.getLineAccountId() + ")";
            } else {
                // Support-character/group-chat mode: only override the sender name/icon when
                // the target account has opted in — otherwise send as the Official Account
                // itself, unchanged from before this feature existed.
                String senderName = null;
                String senderIconUrl = null;
                if (Boolean.TRUE.equals(account.getIsGroupChatMode()) && msg.getAdminUserId() != null) {
                    AdminUser admin = adminUserRepository.findById(msg.getAdminUserId()).orElse(null);
                    if (admin != null) {
                        senderName = (admin.getDisplayName() != null && !admin.getDisplayName().trim().isEmpty())
                                ? admin.getDisplayName() : admin.getName();
                        senderIconUrl = admin.getAvatarUrl();
                    }
                }
                // LINE画像挿入: the message's (or its broadcast's) images, sent after the text
                List<String> imageUrls = new java.util.ArrayList<>();
                if (messageImageService != null) {
                    for (Long imageId : messageImageService.existingImageIdsOf(msg)) imageUrls.add(messageImageService.publicUrl(imageId));
                }
                OutboundLineService.LineSendRequest req = new OutboundLineService.LineSendRequest(
                        aes.decrypt(account.getAccessToken()),
                        msg.getToAddress(),
                        transmitBody == null ? "" : transmitBody,
                        senderName, senderIconUrl, imageUrls);
                OutboundLineService.SendResult result = outboundLineService.send(req);
                success = result.success;
                retriable = result.retriable;
                errorMessage = result.errorMessage;
            }
        } else {
            String smtpPwd = pool == null ? null : aes.decrypt(pool.getSmtpPassword());
            String smtpHost = pool == null ? null : pool.getSmtpHost();
            Integer smtpPort = pool == null ? null : pool.getSmtpPort();
            String smtpUser = pool == null ? null : pool.getSmtpUsername();
            OutboundMailService.OutboundRequest req = new OutboundMailService.OutboundRequest(
                    msg.getFromAddress(),
                    msg.getToAddress(),
                    mailMessageTemplateService != null ? mailMessageTemplateService.sentSubjectOf(msg)
                            : (msg.getSubject() == null ? "" : msg.getSubject()),
                    transmitBody == null ? "" : transmitBody,
                    smtpHost,
                    smtpPort == null ? 587 : smtpPort,
                    smtpUser,
                    smtpPwd,
                    supportDisplayName(msg));
            OutboundMailService.SendResult result = outboundMailService.send(req);
            success = result.success;
            retriable = result.retriable;
            errorMessage = result.errorMessage;
        }
        int attempts = (msg.getSendAttempts() == null ? 0 : msg.getSendAttempts()) + 1;
        msg.setSendAttempts(attempts);

        boolean finalOutcome;
        if (success) {
            msg.setStatus(Message.STATUS_SENT);
            msg.setSentAt(LocalDateTime.now());
            msg.setErrorMessage(null);
            msg.setNextRetryAt(null);
            finalOutcome = true;
        } else if (retriable && attempts < MAX_SEND_ATTEMPTS) {
            // Transient failure — put back on the queue with exponential backoff.
            msg.setStatus(Message.STATUS_QUEUED);
            msg.setErrorMessage(errorMessage);
            msg.setNextRetryAt(LocalDateTime.now().plus(backoffFor(attempts)));
            // Counter update is deferred: broadcast counter only moves on final outcome.
            messageRepository.save(msg);
            return;
        } else {
            msg.setStatus(Message.STATUS_FAILED);
            msg.setErrorMessage(errorMessage);
            msg.setNextRetryAt(null);
            finalOutcome = false;
        }
        messageRepository.save(msg);
        if (msg.getBroadcastId() != null) {
            try {
                ctx.getBean(BroadcastService.class).reportMessageCompleted(msg.getBroadcastId(), finalOutcome);
            } catch (Exception e) {
                // defensive — don't fail a send because counter update failed
            }
        } else if (Message.DIR_OUT.equals(msg.getDirection()) && msg.getUserId() != null) {
            // 送信時点で既読 — an individual (non-broadcast) OUT send to this user IS the
            // response to whatever they had pending, whether it was dispatched immediately
            // or (LINE auto-reply's 何分後に返信) only just now after sitting QUEUED. Covers
            // auto-reply at whatever moment it actually fires, without a separate call at
            // compose/queue time that would fire too early for a delayed reply
            // (2026-09-23 client request).
            markThreadAsRead(msg.getUserId());
        }
    }

    /**
     * Substitutes %reply_url% and/or %external_url% into {@code msg} and sets both BODY_TEXT
     * (always the full text, for メッセージボックス) and SENT_BODY_TEXT (what's actually
     * transmitted — 15-char-clipped only when %reply_url% is present; %external_url%-only
     * bodies are sent in full, since the clip rule is specific to %reply_url%'s メッセージ
     * ボックス round-trip).
     *
     * Static (not an instance method) so {@link BroadcastService} can reuse the exact same
     * logic without taking a MessageService dependency — MessageService.sendNow() already
     * reaches BroadcastService via a lazy ApplicationContext lookup specifically to avoid that
     * circular wiring, so the reverse dependency shouldn't be introduced here either.
     *
     * @param msg          the message being composed; must already have replyPageToken set
     *                     (i.e. createReplyPageFor/createShortReplyPageFor already ran)
     * @param renderedBody the placeholder-substituted body, still containing the literal
     *                     %reply_url% / %external_url% tokens
     * @param replyUrl     the already-expanded %reply_url% URL (from ReplyPageService) — reused
     *                     here rather than rebuilt, since token generation is a one-shot side
     *                     effect that already happened when the caller obtained it
     * @param clipLength   channel-specific clip length (caller resolves EMAIL vs SMS — see
     *                     DomainSettingService#getEmailReplyUrlClipLength /
     *                     SmsSettingService#getReplyUrlClipLength)
     */
    static void applyUrlPlaceholders(Message msg, String renderedBody, String replyUrl,
                                      DomainSettingService domainSettingService, int clipLength) {
        // 本文の通りに送信 (2026-10-07 client request): each tag becomes the bare URL right where
        // it was typed — no automatic line break or URL前文言 is added any more, for any channel.
        String decoratedReplyUrl = replyUrl;
        String externalUrl = domainSettingService.buildExternalUrl(msg.getReplyPageToken());
        String decoratedExternalUrlOrEmpty = externalUrl == null ? "" : externalUrl;

        String fullBody = renderedBody
                .replace(REPLY_URL_PLACEHOLDER, decoratedReplyUrl)
                .replace(EXTERNAL_URL_PLACEHOLDER, decoratedExternalUrlOrEmpty);
        msg.setBodyText(fullBody);

        boolean hasReplyUrlTag = renderedBody.contains(REPLY_URL_PLACEHOLDER);
        if (hasReplyUrlTag) {
            // clipForTransmission locates %reply_url% itself; %external_url% (if also present)
            // is substituted first so no raw tag text leaks into the transmitted message even
            // when it falls before the clip boundary. Only the BODY text before the tag is
            // subject to the 15-char limit.
            String bodyWithExternalResolved = renderedBody.replace(EXTERNAL_URL_PLACEHOLDER, decoratedExternalUrlOrEmpty);
            msg.setSentBodyText(clipForTransmission(bodyWithExternalResolved, decoratedReplyUrl, clipLength));
        } else {
            msg.setSentBodyText(null);
        }
    }

    /**
     * 15-char clip rule (メッセージボックス feature): what's actually TRANSMITTED when the body
     * contains %reply_url% is the first {@link #REPLY_URL_CLIP_LENGTH} characters of the text
     * BEFORE the tag, plus the expanded URL once — the tag itself is located first and removed
     * whole, then the remaining text is clipped, rather than clipping first and hoping the tag
     * happens to still be intact inside the clipped window. The naive "clip first, then try to
     * remove the literal %reply_url% string" approach broke whenever the 15-char boundary fell
     * INSIDE the tag (e.g. body = "本日まで\n%reply_url%", where "本日まで\n" is only 6 chars,
     * so the clip window ends mid-tag at "...%reply_ur") — the substring no longer contained the
     * exact token, so replace() silently did nothing and the mangled tag fragment
     * ("%reply_urhttps://...") was transmitted to the user. Locating the tag first and clipping
     * only the text around it makes this correct regardless of where the tag falls.
     */
    public static String clipForTransmission(String renderedBodyBeforeUrlSwap, String expandedUrl, int clipLength) {
        String body = renderedBodyBeforeUrlSwap == null ? "" : renderedBodyBeforeUrlSwap;
        String url = expandedUrl == null ? "" : expandedUrl;
        int tagIndex = body.indexOf(REPLY_URL_PLACEHOLDER);
        if (tagIndex < 0) {
            // No literal tag present (shouldn't normally happen — callers only invoke this
            // when the tag was detected — but stay correct if it's absent): clip the whole body.
            return body.length() > clipLength ? body.substring(0, clipLength) : body;
        }
        String beforeTag = body.substring(0, tagIndex);
        if (beforeTag.length() <= clipLength) return beforeTag + url;   // as written
        // The text was cut short — put the URL on its own line so it doesn't run into the
        // clipped text (the line break the operator typed before the tag may have been cut off).
        String prefix = beforeTag.substring(0, clipLength);
        return prefix.endsWith("\n") ? prefix + url : prefix + "\n" + url;
    }

    /** Max transient-retry attempts before giving up and marking FAILED. */
    private static final int MAX_SEND_ATTEMPTS = 6;

    /** Exponential backoff: 1min, 2min, 4min, 8min, 16min, 30min cap. */
    private static java.time.Duration backoffFor(int attempt) {
        long minutes = Math.min(30L, 1L << Math.max(0, attempt - 1));
        return java.time.Duration.ofMinutes(minutes);
    }

    @Transactional
    public boolean cancelScheduled(Long messageId) {
        Optional<Message> opt = messageRepository.findById(messageId);
        if (!opt.isPresent()) return false;
        Message m = opt.get();
        if (!Message.STATUS_QUEUED.equals(m.getStatus())) return false;
        m.setStatus(Message.STATUS_CANCELLED);
        messageRepository.save(m);
        return true;
    }

    /**
     * Re-dispatch a FAILED (or CANCELLED) outbound message. Looks up the pool by the
     * original from-address. Returns true if re-send was attempted.
     */
    @Transactional
    public boolean retrySend(Long messageId) {
        Optional<Message> opt = messageRepository.findById(messageId);
        if (!opt.isPresent()) return false;
        Message m = opt.get();
        if (!Message.DIR_OUT.equals(m.getDirection())) return false;
        // Only allow retry for terminal failure-ish states
        if (!Message.STATUS_FAILED.equals(m.getStatus())
                && !Message.STATUS_CANCELLED.equals(m.getStatus())) return false;
        if (m.getFromAddress() == null) return false;
        // Pool is best-effort post-refactor: outbound transport doesn't require it any more.
        CarrierAddressPool pool = poolRepository.findByAddress(m.getFromAddress()).orElse(null);
        // Reset to QUEUED state so the send path runs cleanly
        m.setStatus(Message.STATUS_QUEUED);
        m.setErrorMessage(null);
        messageRepository.save(m);
        sendNow(m, pool);
        return true;
    }

    public int unreadInboundCount(Long userId) {
        return messageRepository.countByUserIdAndDirectionAndReadAtIsNull(userId, Message.DIR_IN);
    }

    public static class MessageException extends RuntimeException {
        public MessageException(String msg) { super(msg); }
    }
}
