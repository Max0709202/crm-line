package com.crm.service;

import com.crm.dto.BroadcastForm;
import com.crm.entity.Broadcast;
import com.crm.entity.CarrierAddressPool;
import com.crm.entity.CrmUser;
import com.crm.entity.Message;
import com.crm.entity.LineUser;
import com.crm.repository.BroadcastRepository;
import com.crm.repository.CarrierAddressPoolRepository;
import com.crm.repository.CrmUserRepository;
import com.crm.repository.LineUserRepository;
import com.crm.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class BroadcastService {

    private static final Logger log = LoggerFactory.getLogger(BroadcastService.class);

    private final BroadcastRepository broadcastRepository;
    private final CrmUserRepository userRepository;
    private final CarrierAddressPoolRepository poolRepository;
    private final CarrierBindingService bindingService;
    private final MessageRepository messageRepository;
    private final PlaceholderService placeholderService;
    private final ReplyPageService replyPageService;
    private final DomainSettingService domainSettingService;
    private final ReplyPageSettingService replyPageSettingService;
    private final SmsSettingService smsSettingService;
    private final LineUserRepository lineUserRepository;
    private final LineAccountService lineAccountService;

    /** LINE送信テキスト (最大文字数・固定テンプレート) — optional so hand-built instances (tests) work without it. */
    private LineTextService lineTextService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setLineTextService(LineTextService lineTextService) { this.lineTextService = lineTextService; }

    /** 送信キャラ — optional so hand-built instances (tests) work without it. */
    private CharaLinkService charaLinkService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setCharaLinkService(CharaLinkService charaLinkService) { this.charaLinkService = charaLinkService; }

    /** サポート窓口 — sender of a send with no 送信キャラ (キャラ指定なし). Optional (tests). */
    private SupportDeskService supportDeskService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSupportDeskService(SupportDeskService supportDeskService) { this.supportDeskService = supportDeskService; }

    /** The name a mail goes out as (%staff_name%): the 送信キャラ, or with キャラ指定なし the サポート窓口
     *  (its 送信者名, else "サポート窓口"). */
    private String senderName(Long charaId) {
        String chara = charaLinkService == null ? null : charaLinkService.charaName(charaId);
        if (chara != null) return chara;
        String support = supportDeskService == null ? "" : supportDeskService.senderName();
        return support.isEmpty() ? "サポート窓口" : support;
    }

    /** 画像添付 / LINE画像挿入 — optional (tests). */
    private MessageImageService messageImageService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setMessageImageService(MessageImageService messageImageService) { this.messageImageService = messageImageService; }

    /** 画像添付 (メール / SMS) / 画像挿入 (LINE) of the form, checked; NoTargetsException carries a bad choice. */
    private List<Long> validImages(BroadcastForm form, boolean forLine) {
        if (messageImageService == null || form.getImageIds() == null || form.getImageIds().isEmpty()) return new ArrayList<>();
        try {
            return messageImageService.validIds(form.getImageIds(), forLine);
        } catch (MessageImageService.ImageException e) {
            throw new NoTargetsException(e.getMessage());
        }
    }

    private void attachImages(Broadcast saved, List<Long> imageIds) {
        if (messageImageService != null && !imageIds.isEmpty()) {
            messageImageService.attach(com.crm.entity.MessageImage.OWNER_BROADCAST, saved.getId(), imageIds);
        }
    }

    /** メールテンプレート設定 › メール通知 as the form of a キャラ mail — optional (tests). */
    private MailMessageTemplateService mailMessageTemplateService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setMailMessageTemplateService(MailMessageTemplateService s) { this.mailMessageTemplateService = s; }

    public BroadcastService(BroadcastRepository broadcastRepository,
                            CrmUserRepository userRepository,
                            CarrierAddressPoolRepository poolRepository,
                            CarrierBindingService bindingService,
                            MessageRepository messageRepository,
                            PlaceholderService placeholderService,
                            ReplyPageService replyPageService,
                            DomainSettingService domainSettingService,
                            ReplyPageSettingService replyPageSettingService,
                            SmsSettingService smsSettingService,
                            LineUserRepository lineUserRepository,
                            LineAccountService lineAccountService) {
        this.broadcastRepository = broadcastRepository;
        this.userRepository = userRepository;
        this.poolRepository = poolRepository;
        this.bindingService = bindingService;
        this.messageRepository = messageRepository;
        this.placeholderService = placeholderService;
        this.domainSettingService = domainSettingService;
        this.replyPageService = replyPageService;
        this.replyPageSettingService = replyPageSettingService;
        this.smsSettingService = smsSettingService;
        this.lineUserRepository = lineUserRepository;
        this.lineAccountService = lineAccountService;
    }

    public Page<Broadcast> list(int page, int size) {
        return broadcastRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    public Optional<Broadcast> findById(Long id) {
        return broadcastRepository.findById(id);
    }

    public List<Broadcast> findAllByIds(java.util.Collection<Long> ids) {
        return broadcastRepository.findAllById(ids);
    }

    /**
     * Create a broadcast and pre-materialise per-user MESSAGE rows with staggered SCHEDULED_AT
     * so the existing scheduler naturally throttles by rate-per-minute.
     *
     * Throws {@link NoTargetsException} if there are no deliverable users — rather than
     * silently saving an empty broadcast that looks stuck in SENDING forever.
     *
     * {@code noRollbackFor}: a caller that itself runs inside a transaction (e.g.
     * DiffScheduleService#execute, which shares this method's transaction — propagation
     * REQUIRED by default) and catches this exception to record a FAILED status still fails
     * at commit with UnexpectedRollbackException otherwise — Spring's transactional advice
     * marks the whole physical transaction rollback-only the instant this method throws,
     * regardless of whether the exception is later caught further up the call stack. Safe
     * here because the check runs before any row is written in this method.
     */
    @Transactional(noRollbackFor = NoTargetsException.class)
    public Broadcast createAndQueue(BroadcastForm form, Long adminUserId) {
        if ("SMS".equals(form.getChannel())) {
            return createAndQueueSms(form, adminUserId);
        }
        if ("LINE".equals(form.getChannel())) {
            return createAndQueueLine(form, adminUserId);
        }
        List<Long> imageIds = validImages(form, false);
        List<CrmUser> targets = findTargetUsers(form);

        // Pre-compute which targets are actually deliverable (have at least one active pool
        // binding AND aren't blocked by an RFC-invalid local-part on a carrier whose SMTP
        // refuses such addresses). Carrier-by-carrier policy:
        //   * docomo.ne.jp — the relay (obob.jar, 2026-05-21 quoteIfDotProblematic patch)
        //     rewraps trailing-/leading-/double-dot local-parts into RFC 5321 quoted-string
        //     form ("foo."@docomo.ne.jp), which docomo's MX accepts. We let these through.
        //   * everywhere else (gmail.com / yahoo.co.jp / icloud.com / au.com / …) — their
        //     MX servers reject dot-issue addresses even in quoted form, so skip pre-dispatch.
        // Unbound (no pool / inactive-pool) users used to be skipped, but the operator embeds
        // the reply URL in the body and doesn't need a working FROM-address for round-tripping.
        // 2026-05-24: fall back to the configured base-domain FROM (from.base_domain setting)
        // so a broadcast can still be sent to users who haven't been carrier-bound yet.
        List<CrmUser> deliverable = new java.util.ArrayList<>();
        int unbound = 0, poolMissing = 0;
        java.util.List<Long> unsendableIds = new java.util.ArrayList<>();
        java.util.Map<Long, CarrierAddressPool> userToPool = new java.util.HashMap<>();
        String fallbackFrom = domainSettingService.buildFromAddress();
        for (CrmUser u : targets) {
            // SMS-only users (registered via CSV import with phone but no email) have no
            // TO_ADDRESS for an email broadcast — route them to createAndQueueSms instead.
            if (u.getEmail() == null || u.getEmail().isEmpty()) {
                unsendableIds.add(u.getId());
                continue;
            }
            if (u.getAddressInvalidReason() != null && !u.getAddressInvalidReason().isEmpty()
                    && !isDocomoDotIssueRescuable(u.getEmail())) {
                unsendableIds.add(u.getId());
                continue;
            }
            Optional<CarrierAddressPool> pool = bindingService.firstBoundFor(u.getId());
            if (pool.isPresent() && !Boolean.FALSE.equals(pool.get().getIsActive())) {
                deliverable.add(u);
                userToPool.put(u.getId(), pool.get());
            } else if (fallbackFrom != null && !fallbackFrom.isEmpty()) {
                // No carrier binding (or pool inactive) — accept the user and use the
                // base-domain FROM. userToPool intentionally has NO entry; the message
                // generator below detects this and uses fallbackFrom.
                if (pool.isPresent()) poolMissing++;
                else                  unbound++;
                deliverable.add(u);
            } else {
                if (pool.isPresent()) poolMissing++;
                else                  unbound++;
            }
        }

        if (deliverable.isEmpty()) {
            throw new NoTargetsException(
                    "条件に合致し、送信可能なユーザーが見つかりませんでした。"
                  + " (絞り込みに合致したユーザー: " + targets.size()
                  + "件、うちアドレス形式エラー: " + unsendableIds.size()
                  + "件、キャリアアドレス未割当: " + unbound
                  + "件、プール側で無効: " + poolMissing
                  + "件、フォールバックFROM未設定: " + (fallbackFrom == null || fallbackFrom.isEmpty() ? "はい" : "いいえ") + ")");
        }

        Broadcast b = new Broadcast();
        b.setAdminUserId(adminUserId);
        String t = form.getTitle();
        b.setTitle((t == null || t.trim().isEmpty()) ? form.getSubject().trim() : t.trim());
        b.setSubject(form.getSubject());
        b.setBodyText(form.getBody());
        b.setChannel("EMAIL");
        b.setRatePerMinute(form.getRatePerMinute() == null || form.getRatePerMinute() < 1
                ? 60 : form.getRatePerMinute());
        b.setTargetFilter(buildFilterSummary(form, targets.size(), unbound + poolMissing));
        b.setTotalCount(deliverable.size());
        b.setUnsendableCount(unsendableIds.size());
        if (!unsendableIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < unsendableIds.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(unsendableIds.get(i));
            }
            b.setUnsendableUserIds(sb.toString());
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startAt = form.getScheduledAt() != null && form.getScheduledAt().isAfter(now)
                ? form.getScheduledAt() : now;
        b.setScheduledAt(form.getScheduledAt());
        b.setStatus(startAt.isAfter(now) ? Broadcast.STATUS_SCHEDULED : Broadcast.STATUS_SENDING);
        Broadcast saved = broadcastRepository.save(b);
        // 送信キャラ (一斉送信 / SMS配信 / 差分ステップ で選択)
        if (charaLinkService != null) charaLinkService.assign(com.crm.entity.CharaRef.OWNER_BROADCAST, saved.getId(), form.getCharaId());
        attachImages(saved, imageIds);

        long intervalMs = 60_000L / b.getRatePerMinute();
        // キャラ指定なし = サポート窓口: the メールテンプレート設定 › サポート窓口 template while it is 有効,
        // else メール通知 as before
        boolean fromSupport = (charaLinkService == null ? null : charaLinkService.charaName(form.getCharaId())) == null;
        String templateKey = fromSupport && mailMessageTemplateService != null
                && mailMessageTemplateService.isActive(MailTemplateService.SUPPORT) ? MailTemplateService.SUPPORT : MailTemplateService.NOTICE;
        boolean mailTemplated = mailMessageTemplateService != null && mailMessageTemplateService.isActive(templateKey);
        String charaName = mailTemplated ? senderName(form.getCharaId()) : null;
        for (int i = 0; i < deliverable.size(); i++) {
            CrmUser user = deliverable.get(i);
            CarrierAddressPool pool = userToPool.get(user.getId());
            // If user has no active pool binding, use the base-domain FROM fallback.
            // The fallback is rebuilt per-message because it may include a random local-part
            // (see DomainSettingService.resolveFromLocal).
            String fromAddr = (pool != null)
                    ? pool.getAddress()
                    : domainSettingService.buildFromAddress();

            LocalDateTime when = startAt.plusNanos(intervalMs * 1_000_000L * i);
            Message m = new Message();
            m.setUserId(user.getId());
            m.setAdminUserId(adminUserId);
            m.setDirection(Message.DIR_OUT);
            m.setChannel(Message.CHANNEL_BROADCAST);
            m.setSubject(placeholderService.substitute(form.getSubject(), user, when.toLocalDate()));
            String body = MessageService.withReplyUrlForImages(placeholderService.substitute(form.getBody(), user, when.toLocalDate()), imageIds);
            m.setBodyText(body);
            m.setFromAddress(fromAddr);
            m.setToAddress(user.getEmail());
            m.setBroadcastId(saved.getId());
            m.setStatus(Message.STATUS_QUEUED);
            m.setScheduledAt(when);
            Message persisted = messageRepository.save(m);

            boolean needsAnyUrl = body.contains(MessageService.REPLY_URL_PLACEHOLDER)
                    || body.contains(MessageService.EXTERNAL_URL_PLACEHOLDER);
            if (mailTemplated) {
                // メール通知 (with %body%) is the form of the mail: the 文字数設定 counts only %body%
                mailMessageTemplateService.apply(persisted, user, persisted.getSubject(), body, charaName, templateKey);
                messageRepository.save(persisted);
            } else if (needsAnyUrl) {
                String url = replyPageService.createReplyPageFor(persisted);
                // Same full-body-vs-clipped-transmit split as MessageService.compose() —
                // see MessageService.applyUrlPlaceholders() / clipForTransmission().
                MessageService.applyUrlPlaceholders(persisted, body, url, domainSettingService,
                        domainSettingService.getEmailReplyUrlClipLength());
                // Historical/audit record only — メッセージボックス visibility is decided at
                // VIEW time now (see MessageBoxService#listFor), not from this send-time flag.
                persisted.setExcludedFromBox(domainSettingService.isActiveLinkDomainExternalLanding());
                messageRepository.save(persisted);
            }
        }
        log.info("Broadcast {} created: {} queued (filter matched {}, skipped invalid-address {}, no-binding {}, pool-inactive {})",
                saved.getId(), saved.getTotalCount(), targets.size(),
                unsendableIds.size(), unbound, poolMissing);
        return saved;
    }

    public static class NoTargetsException extends RuntimeException {
        public NoTargetsException(String msg) { super(msg); }
    }

    /**
     * SMS broadcast path — deliberately separate from the email path above, which is built
     * around carrier-pool FROM addresses and RFC-local-part quirks that don't apply to SMS.
     * Deliverability here is simply "has a phone number"; the sender identity comes from
     * {@link SmsSettingService} rather than a per-user carrier pool.
     */
    @Transactional(noRollbackFor = NoTargetsException.class)
    public Broadcast createAndQueueSms(BroadcastForm form, Long adminUserId) {
        List<Long> imageIds = validImages(form, false);
        List<CrmUser> targets = findTargetUsers(form);

        List<CrmUser> deliverable = new ArrayList<>();
        List<Long> unsendableIds = new ArrayList<>();
        for (CrmUser u : targets) {
            String phone = u.getPhoneNumber();
            if (phone != null && !phone.trim().isEmpty()) {
                deliverable.add(u);
            } else {
                unsendableIds.add(u.getId());
            }
        }

        if (deliverable.isEmpty()) {
            throw new NoTargetsException(
                    "条件に合致し、送信可能な電話番号登録済みユーザーが見つかりませんでした。"
                  + " (絞り込みに合致したユーザー: " + targets.size()
                  + "件、うち電話番号未登録: " + unsendableIds.size() + "件)");
        }

        Broadcast b = new Broadcast();
        b.setAdminUserId(adminUserId);
        String t = form.getTitle();
        String label = (t == null || t.trim().isEmpty()) ? "SMS配信" : t.trim();
        b.setTitle(label);
        b.setSubject(label);
        b.setBodyText(form.getBody());
        b.setChannel("SMS");
        b.setRatePerMinute(form.getRatePerMinute() == null || form.getRatePerMinute() < 1
                ? 60 : form.getRatePerMinute());
        b.setTargetFilter(buildFilterSummary(form, targets.size(), unsendableIds.size()));
        b.setTotalCount(deliverable.size());
        b.setUnsendableCount(unsendableIds.size());
        if (!unsendableIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < unsendableIds.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(unsendableIds.get(i));
            }
            b.setUnsendableUserIds(sb.toString());
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startAt = form.getScheduledAt() != null && form.getScheduledAt().isAfter(now)
                ? form.getScheduledAt() : now;
        b.setScheduledAt(form.getScheduledAt());
        b.setStatus(startAt.isAfter(now) ? Broadcast.STATUS_SCHEDULED : Broadcast.STATUS_SENDING);
        Broadcast saved = broadcastRepository.save(b);
        // 送信キャラ (一斉送信 / SMS配信 / 差分ステップ で選択)
        if (charaLinkService != null) charaLinkService.assign(com.crm.entity.CharaRef.OWNER_BROADCAST, saved.getId(), form.getCharaId());
        attachImages(saved, imageIds);

        long intervalMs = 60_000L / b.getRatePerMinute();
        for (int i = 0; i < deliverable.size(); i++) {
            CrmUser user = deliverable.get(i);
            LocalDateTime when = startAt.plusNanos(intervalMs * 1_000_000L * i);
            Message m = new Message();
            m.setUserId(user.getId());
            m.setAdminUserId(adminUserId);
            m.setDirection(Message.DIR_OUT);
            m.setChannel(Message.CHANNEL_SMS);
            String body = MessageService.withReplyUrlForImages(placeholderService.substitute(form.getBody(), user, when.toLocalDate()), imageIds);
            m.setBodyText(body);
            // Resolved per-recipient (not once for the whole broadcast) so RANDOM_* sender-name
            // modes actually rotate identities across the batch instead of reusing one value.
            m.setFromAddress(smsSettingService.resolveSenderName());
            m.setToAddress(user.getPhoneNumber());
            m.setBroadcastId(saved.getId());
            m.setStatus(Message.STATUS_QUEUED);
            m.setScheduledAt(when);
            Message persisted = messageRepository.save(m);

            boolean needsAnyUrl = body.contains(MessageService.REPLY_URL_PLACEHOLDER)
                    || body.contains(MessageService.EXTERNAL_URL_PLACEHOLDER);
            if (needsAnyUrl) {
                // Short (10-char) token — SMS is billed per ~65-char segment.
                String url = replyPageService.createShortReplyPageFor(persisted);
                MessageService.applyUrlPlaceholders(persisted, body, url, domainSettingService,
                        smsSettingService.getReplyUrlClipLength());
                // Historical/audit record only — メッセージボックス visibility is decided at
                // VIEW time now (see MessageBoxService#listFor), not from this send-time flag.
                persisted.setExcludedFromBox(domainSettingService.isActiveLinkDomainExternalLanding());
                messageRepository.save(persisted);
            }
        }
        log.info("SMS broadcast {} created: {} queued (filter matched {}, skipped no-phone {})",
                saved.getId(), saved.getTotalCount(), targets.size(), unsendableIds.size());
        return saved;
    }

    /**
     * LINE broadcast — mirrors {@link #createAndQueueSms}. Unlike email/SMS there is no
     * single "identity" to send from; the operator picks a specific {@code LineAccount}
     * (form.getLineAccountId()) and only targets already linked to THAT account (via a
     * {@link LineUser} row) are deliverable — LINE only allows messaging contacts who have
     * already followed the Official Account being sent from, per-account.
     */
    @Transactional(noRollbackFor = NoTargetsException.class)
    public Broadcast createAndQueueLine(BroadcastForm form, Long adminUserId) {
        Long lineAccountId = form.getLineAccountId();
        if (lineAccountId == null) {
            throw new NoTargetsException("送信元のLINEアカウントを選択してください");
        }
        List<Long> imageIds = validImages(form, true);   // LINE画像挿入
        List<CrmUser> targets = findTargetUsers(form);

        java.util.Map<Long, LineUser> lineUserByCrmUserId = new java.util.HashMap<>();
        java.util.List<Long> targetIds = new ArrayList<>();
        for (CrmUser u : targets) targetIds.add(u.getId());
        for (LineUser lu : lineUserRepository.findByLineAccountIdAndCrmUserIdIn(lineAccountId, targetIds)) {
            if (lu.getCrmUserId() != null) lineUserByCrmUserId.put(lu.getCrmUserId(), lu);
        }

        List<CrmUser> deliverable = new ArrayList<>();
        List<Long> unsendableIds = new ArrayList<>();
        for (CrmUser u : targets) {
            if (lineUserByCrmUserId.containsKey(u.getId())) {
                deliverable.add(u);
            } else {
                unsendableIds.add(u.getId());
            }
        }

        if (deliverable.isEmpty()) {
            throw new NoTargetsException(
                    "条件に合致し、選択したLINEアカウントと連携済みのユーザーが見つかりませんでした。"
                  + " (絞り込みに合致したユーザー: " + targets.size()
                  + "件、うち未連携: " + unsendableIds.size() + "件)");
        }

        Broadcast b = new Broadcast();
        b.setAdminUserId(adminUserId);
        String t = form.getTitle();
        String label = (t == null || t.trim().isEmpty()) ? "LINE配信" : t.trim();
        b.setTitle(label);
        b.setSubject(label);
        b.setBodyText(form.getBody());
        b.setChannel("LINE");
        b.setRatePerMinute(form.getRatePerMinute() == null || form.getRatePerMinute() < 1
                ? 60 : form.getRatePerMinute());
        b.setTargetFilter(buildFilterSummary(form, targets.size(), unsendableIds.size()));
        b.setTotalCount(deliverable.size());
        b.setUnsendableCount(unsendableIds.size());
        if (!unsendableIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < unsendableIds.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(unsendableIds.get(i));
            }
            b.setUnsendableUserIds(sb.toString());
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startAt = form.getScheduledAt() != null && form.getScheduledAt().isAfter(now)
                ? form.getScheduledAt() : now;
        b.setScheduledAt(form.getScheduledAt());
        b.setStatus(startAt.isAfter(now) ? Broadcast.STATUS_SCHEDULED : Broadcast.STATUS_SENDING);
        Broadcast saved = broadcastRepository.save(b);
        attachImages(saved, imageIds);

        long intervalMs = 60_000L / b.getRatePerMinute();
        for (int i = 0; i < deliverable.size(); i++) {
            CrmUser user = deliverable.get(i);
            LineUser lineUser = lineUserByCrmUserId.get(user.getId());
            LocalDateTime when = startAt.plusNanos(intervalMs * 1_000_000L * i);
            Message m = new Message();
            m.setUserId(user.getId());
            m.setAdminUserId(adminUserId);
            m.setDirection(Message.DIR_OUT);
            m.setChannel(Message.CHANNEL_LINE);
            m.setLineAccountId(lineAccountId);
            String body = placeholderService.substitute(form.getBody(), user, when.toLocalDate());
            m.setBodyText(body);
            m.setToAddress(lineUser.getLineUserId());
            m.setBroadcastId(saved.getId());
            m.setStatus(Message.STATUS_QUEUED);
            m.setScheduledAt(when);
            Message persisted = messageRepository.save(m);

            boolean needsAnyUrl = body.contains(MessageService.REPLY_URL_PLACEHOLDER)
                    || body.contains(MessageService.EXTERNAL_URL_PLACEHOLDER);
            if (lineTextService != null) {
                // LINE設定: 最大文字数を超えた分は返信URL先へ・固定テンプレート・短縮URL (LineTextService)
                lineTextService.apply(persisted, user, body);
                messageRepository.save(persisted);
            } else if (needsAnyUrl) {
                String url = replyPageService.createReplyPageFor(persisted);
                MessageService.applyUrlPlaceholders(persisted, body, url, domainSettingService,
                        MessageService.LINE_REPLY_URL_CLIP_LENGTH);
                persisted.setExcludedFromBox(domainSettingService.isActiveLinkDomainExternalLanding());
                messageRepository.save(persisted);
            }
        }
        log.info("LINE broadcast {} created: {} queued (filter matched {}, skipped unlinked {})",
                saved.getId(), saved.getTotalCount(), targets.size(), unsendableIds.size());
        return saved;
    }

    /**
     * 紐づきアカ — {@code form.getLineAccountId()} is the dynamic sentinel
     * ({@link com.crm.entity.DiffStep#LINE_ACCOUNT_LINKED_DYNAMIC}), so each target is routed
     * to whichever single LINE account they should be treated as linked to right now (see
     * {@link LineAccountService#resolveDynamicLinkedAccountIds} — lowest linkage priority wins,
     * never more than one account per recipient, so exactly one message is ever delivered even
     * when a recipient friended several accounts). One Broadcast is created per distinct
     * resolved account by delegating to {@link #createAndQueueLine}, reusing its existing
     * deliverability/targeting logic unchanged (client request 2026-09-27).
     */
    @Transactional(noRollbackFor = NoTargetsException.class)
    public List<Broadcast> createAndQueueLineDynamic(BroadcastForm form, Long adminUserId) {
        List<CrmUser> targets = findTargetUsers(form);
        List<Long> targetIds = new ArrayList<>();
        for (CrmUser u : targets) targetIds.add(u.getId());

        java.util.Map<Long, Long> resolvedAccountByCrmUserId = lineAccountService.resolveDynamicLinkedAccountIds(targetIds);
        java.util.Map<Long, List<Long>> idsByAccount = new java.util.LinkedHashMap<>();
        for (Long id : targetIds) {
            Long accountId = resolvedAccountByCrmUserId.get(id);
            if (accountId == null) continue;
            idsByAccount.computeIfAbsent(accountId, k -> new ArrayList<>()).add(id);
        }
        if (idsByAccount.isEmpty()) {
            throw new NoTargetsException(
                    "条件に合致し、いずれかのLINEアカウントと連携済みのユーザーが見つかりませんでした。"
                  + " (絞り込みに合致したユーザー: " + targets.size() + "件)");
        }

        List<Broadcast> created = new ArrayList<>();
        for (java.util.Map.Entry<Long, List<Long>> e : idsByAccount.entrySet()) {
            BroadcastForm sub = new BroadcastForm();
            sub.setTitle(form.getTitle());
            sub.setBody(form.getBody());
            sub.setChannel("LINE");
            sub.setLineAccountId(e.getKey());
            sub.setTargetUserIds(e.getValue());
            sub.setRatePerMinute(form.getRatePerMinute());
            sub.setScheduledAt(form.getScheduledAt());
            sub.setImageIds(form.getImageIds());
            created.add(createAndQueueLine(sub, adminUserId));
        }
        return created;
    }

    /**
     * Called by MessageService.sendNow() (or anywhere that finalises a broadcast-linked MESSAGE)
     * to update the denormalised counters and flip the broadcast to COMPLETED when done.
     */
    @Transactional
    public void reportMessageCompleted(Long broadcastId, boolean success) {
        // Atomic counter bump — safe under concurrent senders (no read-modify-write race).
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        broadcastRepository.incrementCounters(broadcastId, success ? 1 : 0, success ? 0 : 1, now);
        // Separate atomic check-and-flip to COMPLETED; skips if CANCELLED.
        broadcastRepository.markCompletedIfDone(broadcastId, now);
    }

    /**
     * Cancel a broadcast and all remaining QUEUED messages under it. Two-part operation:
     *   1. Flip BROADCAST.status → CANCELLED so the dispatcher's per-tick cache picks it up
     *      and stops dispatching any message in this batch that hasn't been sent yet.
     *   2. Bulk UPDATE all QUEUED messages of this broadcast → CANCELLED in one statement.
     *
     * Step 1 has to happen FIRST so the dispatcher's race-condition gate (see
     * ScheduledTaskService.dispatchQueued) sees the cancelled broadcast even while the bulk
     * MESSAGE update is still in progress. Previously this method did per-row saves which
     * could take many seconds for a 5K-row broadcast — during that window the dispatcher
     * was still picking up rows that were technically queued, and they leaked to the relay.
     */
    @Transactional
    public void cancel(Long broadcastId) {
        Optional<Broadcast> opt = broadcastRepository.findById(broadcastId);
        if (!opt.isPresent()) return;
        Broadcast b = opt.get();
        if (Broadcast.STATUS_COMPLETED.equals(b.getStatus())
                || Broadcast.STATUS_CANCELLED.equals(b.getStatus())) return;
        b.setStatus(Broadcast.STATUS_CANCELLED);
        broadcastRepository.save(b);
        // Flush the broadcast status flip immediately so the dispatcher's race gate sees it
        // before we start the (potentially slow) bulk message update.
        broadcastRepository.flush();

        int flipped = messageRepository.cancelQueuedByBroadcastId(broadcastId, java.time.LocalDateTime.now());
        log.info("Broadcast {} cancelled, flipped {} QUEUED messages to CANCELLED", broadcastId, flipped);
    }

    @Transactional
    public int deleteByIds(java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        int n = 0;
        for (Long id : ids) {
            if (id == null) continue;
            try {
                cancel(id);
                broadcastRepository.deleteById(id);
                n++;
            } catch (Exception ignored) {}
        }
        return n;
    }

    /**
     * Targeting precedence:
     *   1. Explicit targetUserIds (from "選択一斉送信" on the user list) — when present, ONLY
     *      these users are matched. Filters are ignored.
     *   2. emailDomain / status filters from the form.
     */
    private List<CrmUser> findTargetUsers(BroadcastForm form) {
        java.util.List<Long> ids = form.getTargetUserIds();
        if (ids != null && !ids.isEmpty()) {
            // Explicit list path — drop nulls and unknown ids silently, deterministic order.
            java.util.List<CrmUser> picked = new ArrayList<>();
            for (CrmUser u : userRepository.findAllById(ids)) picked.add(u);
            picked.sort(java.util.Comparator.comparing(CrmUser::getId));
            return picked;
        }
        Specification<CrmUser> spec = (root, q, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (hasText(form.getTargetCarrierCode())) {
                preds.add(cb.like(root.get("email"), "%@%" + form.getTargetCarrierCode() + "%"));
            }
            if (hasText(form.getTargetStatus())) {
                preds.add(cb.equal(root.get("status"), form.getTargetStatus()));
            }
            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new Predicate[0]));
        };
        return userRepository.findAll(spec);
    }

    private String buildFilterSummary(BroadcastForm form, int matched, int skipped) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"carrier_code\":\"").append(hasText(form.getTargetCarrierCode()) ? form.getTargetCarrierCode() : "").append("\",");
        sb.append("\"status\":\"").append(hasText(form.getTargetStatus()) ? form.getTargetStatus() : "").append("\",");
        sb.append("\"matched_users\":").append(matched).append(",");
        sb.append("\"skipped_users\":").append(skipped).append(",");
        sb.append("\"rate_per_minute\":").append(form.getRatePerMinute() == null ? 60 : form.getRatePerMinute());
        sb.append("}");
        return sb.toString();
    }

    private static boolean hasText(String s) { return s != null && !s.trim().isEmpty(); }

    /**
     * True if the address is a docomo dot-issue local-part that the relay can rescue via
     * RFC 5321 quoted-string ("foo."@docomo.ne.jp). docomo's MX accepts the quoted form;
     * other providers (gmail/yahoo/icloud/au) do not, so they remain unsendable.
     */
    private static boolean isDocomoDotIssueRescuable(String email) {
        if (email == null) return false;
        int at = email.lastIndexOf('@');
        if (at < 1 || at == email.length() - 1) return false;
        String domain = email.substring(at + 1).toLowerCase();
        return "docomo.ne.jp".equals(domain);
    }
}
