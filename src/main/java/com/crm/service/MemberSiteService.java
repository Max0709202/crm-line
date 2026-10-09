package com.crm.service;

import com.crm.entity.Chara;
import com.crm.entity.CharaRef;
import com.crm.entity.CrmUser;
import com.crm.entity.Message;
import com.crm.entity.SupportInquiry;
import com.crm.repository.CharaRepository;
import com.crm.repository.CrmUserRepository;
import com.crm.repository.MessageRepository;
import com.crm.repository.SupportInquiryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 会員ページ (after login): what each page shows and does, with ポイント設定's costs —
 * メール送信 (+ アドレス / 電話番号 / 写真添付), 本文閲覧, プロフィール閲覧, 写真閲覧 (キャラの写真・添付画像),
 * プロフィール変更 / プロフィール写真変更, 検索. A キャラ mail / SMS sent to the member is in the 受信BOX;
 * what the member sends arrives in the 管理画面 as a Web返信 to that キャラ (紐づきキャラ).
 * Points are used in one statement each ({@link UserPointService#spend}); things that were paid to
 * look at stay open ({@link MemberUnlockService}).
 */
@Service
public class MemberSiteService {

    private static final Logger log = LoggerFactory.getLogger(MemberSiteService.class);

    public static final String COST_MAIL = "mail_send";
    public static final String COST_ADDRESS = PointSettingService.CODE_ADDRESS_ATTACH;
    public static final String COST_TEL = PointSettingService.CODE_TEL_ATTACH;
    public static final String COST_PHOTO_ATTACH = "photo_attach";
    public static final String COST_BODY = "body_view";
    public static final String COST_PROFILE = "profile_view";
    public static final String COST_PHOTO = "photo_view";
    public static final String COST_PROFILE_EDIT = "profile_edit";
    public static final String COST_PROFILE_PHOTO = "profile_photo_edit";
    public static final String COST_SEARCH = "search";

    public static final int SUBJECT_MAX = 100;
    public static final int BODY_MAX = 5000;
    private static final int INBOX_MAX = 500;
    /** 受信一覧 / 送信済み: mails per page (11通目から2ページ目). */
    public static final int PAGE_SIZE = 10;
    /** Who a mail with no キャラ is from on the member pages (キャラ指定なし = サポート窓口から送信). */
    public static final String SUPPORT_NAME = "サポート窓口";

    public static class MemberException extends RuntimeException {
        public MemberException(String msg) { super(msg); }
    }

    /** One 受信BOX row. */
    public static final class InboxItem {
        public final Message message;
        public final Chara chara;          // null = サポート窓口 (no キャラ)
        public final boolean unread;
        public final boolean sent;         // 送信済み tab: the member's own message
        public InboxItem(Message message, Chara chara, boolean unread, boolean sent) {
            this.message = message; this.chara = chara; this.unread = unread; this.sent = sent;
        }
    }

    /** 受信BOX row: one キャラ (null = サポート窓口) with its newest mail and how many are 未読. */
    public static final class InboxGroup {
        public final Chara chara;
        public final Message latest;
        public final int unread;
        public InboxGroup(Chara chara, Message latest, int unread) {
            this.chara = chara; this.latest = latest; this.unread = unread;
        }
    }

    /** One message of a conversation with a キャラ. */
    public static final class ConvItem {
        public final Message message;
        public final boolean out;          // from the キャラ
        public final boolean open;         // 本文 visible (本文閲覧 free / used, or the member's own)
        public final List<Long> images;
        public ConvItem(Message message, boolean out, boolean open, List<Long> images) {
            this.message = message; this.out = out; this.open = open; this.images = images;
        }
    }

    /** What the member writes on 返信 (送信画面). */
    public static final class SendInput {
        public String subject;
        public String body;
        public boolean address;
        public boolean tel;
        public MultipartFile photo;
    }

    private final CrmUserRepository userRepository;
    private final MessageRepository messageRepository;
    private final CharaRepository charaRepository;
    private final CharaLinkService charaLinkService;
    private final PointSettingService pointSettingService;
    private final UserPointService userPointService;
    private final MemberUnlockService unlockService;
    private final MessageImageService messageImageService;
    private final UserProfileService userProfileService;
    private final SupportInquiryRepository supportInquiryRepository;
    private final ReplyAttachmentService attachmentService;
    private final UserActivityService userActivityService;
    private final PasswordEncoder passwordEncoder;

    public MemberSiteService(CrmUserRepository userRepository, MessageRepository messageRepository,
                             CharaRepository charaRepository, CharaLinkService charaLinkService,
                             PointSettingService pointSettingService, UserPointService userPointService,
                             MemberUnlockService unlockService, MessageImageService messageImageService,
                             UserProfileService userProfileService, SupportInquiryRepository supportInquiryRepository,
                             ReplyAttachmentService attachmentService, UserActivityService userActivityService,
                             PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.charaRepository = charaRepository;
        this.charaLinkService = charaLinkService;
        this.pointSettingService = pointSettingService;
        this.userPointService = userPointService;
        this.unlockService = unlockService;
        this.messageImageService = messageImageService;
        this.userProfileService = userProfileService;
        this.supportInquiryRepository = supportInquiryRepository;
        this.attachmentService = attachmentService;
        this.userActivityService = userActivityService;
        this.passwordEncoder = passwordEncoder;
    }

    /** メール送信設定's 本文の文字数設定 — how much of a mail the 受信BOX shows. Optional (tests). */
    private DomainSettingService domainSettingService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDomainSettingService(DomainSettingService domainSettingService) { this.domainSettingService = domainSettingService; }

    /** LINE送信 without a キャラ: shown as from the LINE account (its 名前). Optional (tests). */
    private com.crm.repository.LineAccountRepository lineAccountRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setLineAccountRepository(com.crm.repository.LineAccountRepository lineAccountRepository) { this.lineAccountRepository = lineAccountRepository; }

    /** 表示文字数: the 本文 shown in the 受信BOX / 受信一覧 — as much as the mail itself showed. */
    public int previewLength() {
        return domainSettingService == null ? 30 : domainSettingService.getEmailReplyUrlClipLength();
    }

    /* ===================== ログイン ===================== */

    /** 会員ログイン: メールアドレス or ログインID + password, 本登録済み (ACTIVE) members only. */
    public Optional<CrmUser> authenticate(String loginId, String password) {
        String id = loginId == null ? "" : java.text.Normalizer.normalize(loginId, java.text.Normalizer.Form.NFKC).trim();
        if (id.isEmpty() || password == null || password.isEmpty()) return Optional.empty();
        List<CrmUser> candidates = new ArrayList<>();
        if (id.contains("@")) candidates.addAll(userRepository.findAllByEmailIgnoreCase(id));
        else userRepository.findFirstByLoginId(id).ifPresent(candidates::add);
        for (CrmUser u : candidates) {
            if (!CrmUser.STATUS_ACTIVE.equals(u.getStatus()) || u.getLoginPassword() == null) continue;
            if (passwordEncoder.matches(password, u.getLoginPassword())) return Optional.of(u);
        }
        return Optional.empty();
    }

    /** The session's member while 本登録済み (ACTIVE). */
    public Optional<CrmUser> member(Object sessionMemberId) {
        if (!(sessionMemberId instanceof Long)) return Optional.empty();
        return userRepository.findById((Long) sessionMemberId).filter(u -> CrmUser.STATUS_ACTIVE.equals(u.getStatus()));
    }

    public void touch(CrmUser u) {
        try {
            userActivityService.touchLastLogin(u);
        } catch (RuntimeException e) {
            log.debug("member touch failed: {}", e.toString());
        }
    }

    /* ===================== ポイント ===================== */

    public int cost(String code, CrmUser u) {
        return pointSettingService.getCost(code, u.getFolder());
    }

    public boolean offered(String code, CrmUser u) {
        return pointSettingService.isShown(code, u.getFolder());
    }

    public int points(CrmUser u) {
        return userPointService.get(u.getId());
    }

    /* ===================== 受信BOX ===================== */

    private List<Message> inboxMessages(CrmUser u) {
        return messageRepository.findMemberInbox(u.getId(), PageRequest.of(0, INBOX_MAX));
    }

    /** 未読 = a キャラ message whose 本文 the member hasn't opened yet. */
    public int unreadCount(CrmUser u) {
        List<Message> msgs = inboxMessages(u);
        if (msgs.isEmpty()) return 0;
        List<Long> ids = new ArrayList<>();
        for (Message m : msgs) ids.add(m.getId());
        Set<Long> read = unlockService.unlocked(u.getId(), MemberUnlockService.BODY, ids);
        return ids.size() - read.size();
    }

    /**
     * 受信BOX rows: {@code tab} all / unread (未読) / fav (お気に入り = 友達追加したキャラ) / sent (送信済み);
     * {@code charaId} narrows to one キャラ (0 = サポート窓口).
     */
    public List<InboxItem> inbox(CrmUser u, String tab, Long charaId) {
        boolean sentTab = "sent".equals(tab);
        List<Message> msgs = sentTab ? messageRepository.findMemberSent(u.getId(), PageRequest.of(0, INBOX_MAX)) : inboxMessages(u);
        Map<Long, Long> charaByMsg = charaLinkService.charaIdsOfMessages(msgs);
        Map<Long, Chara> charas = charasById(new HashSet<>(charaByMsg.values()));
        Map<Long, Chara> lineSenders = lineSenders(msgs, charaByMsg);
        List<Long> ids = new ArrayList<>();
        for (Message m : msgs) ids.add(m.getId());
        Set<Long> read = sentTab ? Collections.<Long>emptySet() : unlockService.unlocked(u.getId(), MemberUnlockService.BODY, ids);
        Set<Long> friends = new HashSet<>();
        if ("fav".equals(tab)) for (Chara c : charaLinkService.linkedCharas(u.getId())) friends.add(c.getId());
        List<InboxItem> out = new ArrayList<>();
        for (Message m : msgs) {
            Long cid = charaByMsg.get(m.getId());
            Chara c = cid == null ? lineSenders.get(m.getLineAccountId()) : charas.get(cid);
            if (charaId != null && !charaId.equals(c == null ? 0L : c.getId())) continue;
            boolean unread = !sentTab && !read.contains(m.getId());
            if ("unread".equals(tab) && !unread) continue;
            if ("fav".equals(tab) && (c == null || !friends.contains(c.getId()))) continue;
            out.add(new InboxItem(m, c, unread, sentTab));
        }
        return out;
    }

    /**
     * 受信BOX (すべて / 未読 / お気に入り): the キャラ that wrote to the member, one row each, newest mail
     * first, with how many of their mails are 未読. {@code charaId} narrows to one キャラ (0 = サポート窓口).
     */
    public List<InboxGroup> inboxGroups(CrmUser u, String tab, Long charaId) {
        List<InboxItem> items = inbox(u, "fav".equals(tab) ? "fav" : "all", charaId);
        Map<Long, Chara> charaOf = new LinkedHashMap<>();
        Map<Long, Message> latest = new LinkedHashMap<>();
        Map<Long, Integer> unread = new HashMap<>();
        for (InboxItem it : items) {   // newest first
            Long key = it.chara == null ? 0L : it.chara.getId();
            if (!latest.containsKey(key)) {
                latest.put(key, it.message);
                charaOf.put(key, it.chara);
            }
            if (it.unread) unread.merge(key, 1, Integer::sum);
        }
        List<InboxGroup> out = new ArrayList<>();
        for (Map.Entry<Long, Message> e : latest.entrySet()) {
            int n = unread.getOrDefault(e.getKey(), 0);
            if ("unread".equals(tab) && n == 0) continue;
            out.add(new InboxGroup(charaOf.get(e.getKey()), e.getValue(), n));
        }
        return out;
    }

    /** 受信一覧: the mails one キャラ (0 = サポート窓口) sent to the member — received only, newest first. */
    public List<InboxItem> charaInbox(CrmUser u, long charaId) {
        return inbox(u, "all", charaId);
    }

    /** 過去の受信者から選択: the キャラ that wrote to the member (and サポート窓口 when it did). */
    public Map<Long, String> pastSenders(CrmUser u) {
        List<Message> msgs = inboxMessages(u);
        Map<Long, Long> charaByMsg = charaLinkService.charaIdsOfMessages(msgs);
        Map<Long, Chara> charas = charasById(new HashSet<>(charaByMsg.values()));
        Map<Long, Chara> lineSenders = lineSenders(msgs, charaByMsg);
        Map<Long, String> out = new LinkedHashMap<>();
        for (Message m : msgs) {
            Long cid = charaByMsg.get(m.getId());
            Chara c = cid == null ? lineSenders.get(m.getLineAccountId()) : charas.get(cid);
            if (c == null) out.putIfAbsent(0L, SUPPORT_NAME);
            else out.putIfAbsent(c.getId(), c.getName());
        }
        return out;
    }

    /* ===================== キャラ / やり取り ===================== */

    public Optional<Chara> chara(Long charaId) {
        return charaId == null || charaId <= 0 ? Optional.<Chara>empty() : charaRepository.findById(charaId);
    }

    /**
     * A LINE送信 sent without a キャラ is shown in the 受信BOX as from its LINE account: a display-only
     * (never saved) キャラ with the account's 名前 and the key −(LINE account ID), so 受信一覧 / 返信
     * ({@code c=}) reach it. Its mails are read without 本文閲覧 points (the text already came in LINE).
     */
    public Optional<Chara> lineSender(long key) {
        if (key >= 0 || lineAccountRepository == null) return Optional.empty();
        return lineAccountRepository.findById(-key).map(a -> lineSenderOf(a.getId(), a.getName()));
    }

    /** Is {@code c} a LINE account shown as the sender ({@link #lineSender}), not a real キャラ? */
    public static boolean isLineSender(Chara c) {
        return c != null && c.getId() != null && c.getId() < 0;
    }

    private static Chara lineSenderOf(Long accountId, String name) {
        Chara c = new Chara();
        c.setId(-accountId);
        c.setName(name == null || name.trim().isEmpty() ? "LINE" : name);
        return c;
    }

    /** LINE account ID → its {@link #lineSender} for the LINE messages of {@code msgs} that have no キャラ. */
    private Map<Long, Chara> lineSenders(List<Message> msgs, Map<Long, Long> charaByMsg) {
        Map<Long, Chara> out = new HashMap<>();
        if (lineAccountRepository == null) return out;
        Set<Long> accountIds = new HashSet<>();
        for (Message m : msgs) {
            if (Message.CHANNEL_LINE.equals(m.getChannel()) && m.getLineAccountId() != null && !charaByMsg.containsKey(m.getId())) {
                accountIds.add(m.getLineAccountId());
            }
        }
        if (accountIds.isEmpty()) return out;
        for (com.crm.entity.LineAccount a : lineAccountRepository.findAllById(accountIds)) out.put(a.getId(), lineSenderOf(a.getId(), a.getName()));
        return out;
    }

    /**
     * The member's messages with one キャラ (0 = サポート窓口 / no キャラ), oldest first: the キャラ's mails / SMS
     * and the member's own (Web返信 / mail replies). LINE: only what was sent to the member (as in the
     * 受信BOX); a LINE送信 without a キャラ belongs to its LINE account (charaId −account ID, {@link #lineSender}).
     */
    public List<ConvItem> conversation(CrmUser u, long charaId) {
        List<Message> all = new ArrayList<>();
        for (Message m : messageRepository.findByUserIdOrderByCreatedAtAsc(u.getId())) {
            if (Message.CHANNEL_LINE.equals(m.getChannel()) && !Message.DIR_OUT.equals(m.getDirection())) continue;
            if (Message.DIR_OUT.equals(m.getDirection())) {
                if (!Message.STATUS_SENT.equals(m.getStatus()) || m.getBoxDismissedAt() != null) continue;
            }
            all.add(m);
        }
        Map<Long, Long> charaByMsg = charaLinkService.charaIdsOfMessages(all);
        // the same キャラ as the 受信BOX (a LINE account that no longer exists = サポート窓口)
        Map<Long, Chara> lineSenders = lineSenders(all, charaByMsg);
        List<Message> mine = new ArrayList<>();
        for (Message m : all) {
            Long cid = charaByMsg.get(m.getId());
            Chara line = cid == null ? lineSenders.get(m.getLineAccountId()) : null;
            long key = cid != null ? cid : line != null ? line.getId() : 0L;
            if (charaId == key) mine.add(m);
        }
        List<Long> outIds = new ArrayList<>();
        for (Message m : mine) if (Message.DIR_OUT.equals(m.getDirection())) outIds.add(m.getId());
        Set<Long> read = unlockService.unlocked(u.getId(), MemberUnlockService.BODY, outIds);
        // サポート窓口 / a LINE account are not キャラ: their mails are read without 本文閲覧 points
        boolean free = charaId <= 0 || cost(COST_BODY, u) <= 0;
        Map<Long, List<Long>> images = messageImageService.imageIdsOfMessages(mine);
        List<ConvItem> out = new ArrayList<>();
        for (Message m : mine) {
            boolean isOut = Message.DIR_OUT.equals(m.getDirection());
            List<Long> imgs = images.get(m.getId());
            out.add(new ConvItem(m, isOut, !isOut || read.contains(m.getId()) || free,
                    imgs == null ? Collections.<Long>emptyList() : imgs));
        }
        return out;
    }

    /**
     * 本文閲覧 is free: the mail the member clicked in 受信一覧 ({@code messageId}) becomes 既読 —
     * only that one, so the キャラ's other mails stay 未読 until each is opened.
     */
    @Transactional
    public void markReadIfFree(CrmUser u, List<ConvItem> items, Long messageId) {
        if (messageId == null || cost(COST_BODY, u) > 0) return;
        for (ConvItem it : items) {
            if (it.out && messageId.equals(it.message.getId())) {
                unlockService.unlock(u.getId(), MemberUnlockService.BODY, it.message.getId(), 0);
            }
        }
    }

    /**
     * サポート窓口 (no キャラ): opened without ポイント設定's 閲覧pt — the mail clicked in 受信一覧
     * ({@code messageId}) becomes 既読 (only that one), and the attached images of {@code items} open.
     */
    @Transactional
    public void openSupportMails(CrmUser u, List<ConvItem> items, Long messageId) {
        for (ConvItem it : items) {
            if (!it.out) continue;
            if (it.message.getId().equals(messageId)) unlockService.unlock(u.getId(), MemberUnlockService.BODY, it.message.getId(), 0);
            for (Long img : it.images) unlockService.unlock(u.getId(), MemberUnlockService.IMAGE, img, 0);
        }
    }

    /** Does the キャラ message {@code messageId} belong to this member? */
    public boolean isOwnOut(CrmUser u, Long messageId) {
        if (messageId == null) return false;
        return messageRepository.findById(messageId)
                .filter(m -> m.getUserId().equals(u.getId()) && Message.DIR_OUT.equals(m.getDirection()))
                .isPresent();
    }

    /** 本文閲覧 / プロフィール閲覧 / 写真閲覧 with ポイント設定's cost (free when already opened). */
    @Transactional
    public MemberUnlockService.Result open(CrmUser u, String kind, Long refId) {
        String code;
        switch (kind) {
            case MemberUnlockService.BODY:
                if (!isOwnOut(u, refId)) throw new MemberException("メッセージが見つかりません");
                code = COST_BODY;
                break;
            case MemberUnlockService.PROFILE:
                if (!chara(refId).isPresent()) throw new MemberException("お相手が見つかりません");
                code = COST_PROFILE;
                break;
            case MemberUnlockService.PHOTO:
                Optional<Chara> c = chara(refId);
                if (!c.isPresent() || c.get().getPhotoUrl() == null) throw new MemberException("写真はありません");
                code = COST_PHOTO;
                break;
            case MemberUnlockService.IMAGE:
                if (!messageImageService.isImageOfUser(u.getId(), refId)) throw new MemberException("画像が見つかりません");
                code = COST_PHOTO;
                break;
            default:
                throw new MemberException("不明な操作です");
        }
        return unlockService.unlock(u.getId(), kind, refId, cost(code, u));
    }

    /** Is it open for the member (paid before, or free)? */
    public boolean isOpen(CrmUser u, String kind, Long refId) {
        String code = MemberUnlockService.BODY.equals(kind) ? COST_BODY
                : MemberUnlockService.PROFILE.equals(kind) ? COST_PROFILE : COST_PHOTO;
        return cost(code, u) <= 0 || unlockService.isUnlocked(u.getId(), kind, refId);
    }

    public Set<Long> openImages(CrmUser u, Set<Long> imageIds) {
        if (cost(COST_PHOTO, u) <= 0) return imageIds;
        return unlockService.unlocked(u.getId(), MemberUnlockService.IMAGE, imageIds);
    }

    /* ===================== メール送信 ===================== */

    /** Points a send costs with these options (メール送信 + アドレス / 電話番号 / 写真添付). */
    public int sendCost(CrmUser u, boolean address, boolean tel, boolean photo) {
        int total = cost(COST_MAIL, u);
        if (address && offered(COST_ADDRESS, u)) total += cost(COST_ADDRESS, u);
        if (tel && offered(COST_TEL, u)) total += cost(COST_TEL, u);
        if (photo) total += cost(COST_PHOTO_ATTACH, u);
        return total;
    }

    /**
     * The member's message to キャラ {@code charaId} (0 = サポート窓口): arrives in the 管理画面 as a Web返信 to
     * the キャラ's latest message; アドレス / 電話番号添付 add the member's address / number to the text.
     */
    @Transactional
    public Message send(CrmUser u, long charaId, SendInput in, String ip, String ua) {
        if (charaId < 0) throw new MemberException("LINEのメッセージにはLINEからご返信ください");
        Chara chara = null;
        if (charaId > 0) chara = chara(charaId).orElseThrow(() -> new MemberException("お相手が見つかりません"));
        String subject = in.subject == null ? "" : in.subject.trim();
        String body = in.body == null ? "" : in.body.replace("\r\n", "\n").trim();
        if (body.isEmpty()) throw new MemberException("本文を入力してください");
        if (subject.length() > SUBJECT_MAX) throw new MemberException("タイトルは" + SUBJECT_MAX + "文字までです");
        if (body.length() > BODY_MAX) throw new MemberException("本文は" + BODY_MAX + "文字までです");
        boolean address = in.address && offered(COST_ADDRESS, u);
        boolean tel = in.tel && offered(COST_TEL, u);
        if (address && (u.getEmail() == null || u.getEmail().trim().isEmpty())) throw new MemberException("メールアドレスが登録されていません");
        if (tel && (u.getPhoneNumber() == null || u.getPhoneNumber().trim().isEmpty())) throw new MemberException("携帯番号が登録されていません（プロフ編集で登録できます）");
        boolean photo = in.photo != null && !in.photo.isEmpty();
        if (photo) {
            String type = in.photo.getContentType() == null ? "" : in.photo.getContentType().toLowerCase(Locale.ROOT);
            if (!ReplyAttachmentService.ALLOWED_MIME.contains(type)) throw new MemberException("写真は JPEG / PNG / GIF / WebP を選んでください");
            if (in.photo.getSize() > ReplyAttachmentService.MAX_SIZE_BYTES) throw new MemberException("写真は5MB以内にしてください");
        }
        int total = sendCost(u, address, tel, photo);
        if (!userPointService.spend(u.getId(), total)) {
            throw new MemberException("ポイントが足りません（必要 " + total + "pt）。ポイントを購入してからもう一度送信してください");
        }

        StringBuilder text = new StringBuilder(body);
        if (address) text.append("\n\n【アドレス】").append(u.getEmail().trim());
        if (tel) text.append(address ? "\n" : "\n\n").append("【電話番号】").append(u.getPhoneNumber().trim());

        Message latestOut = null;
        for (ConvItem it : conversation(u, charaId)) if (it.out) latestOut = it.message;
        Message msg = new Message();
        msg.setUserId(u.getId());
        msg.setDirection(Message.DIR_IN);
        msg.setChannel(Message.CHANNEL_WEB_REPLY);
        msg.setSubject(subject.isEmpty() ? (latestOut != null ? MessageBoxService.deriveReplyLabel(latestOut) : null) : subject);
        msg.setBodyText(text.toString());
        msg.setFromAddress(u.getEmail() == null || u.getEmail().isEmpty() ? "(会員ページ)" : u.getEmail());
        msg.setToAddress("(会員ページ)");
        msg.setStatus(Message.STATUS_SENT);
        msg.setSentAt(LocalDateTime.now());
        if (latestOut != null) {
            msg.setReplyToMessageId(latestOut.getId());
            msg.setReplyPageToken(latestOut.getReplyPageToken());
        }
        Message saved = messageRepository.save(msg);
        if (chara != null) {
            charaLinkService.assign(CharaRef.OWNER_MESSAGE, saved.getId(), chara.getId());
            charaLinkService.link(u.getId(), chara.getId());
        }
        if (photo) {
            try {
                attachmentService.upload(u.getId(), u.getActiveMemoSlot(), in.photo, ip, saved.getId());
            } catch (ReplyAttachmentService.AttachmentException | java.io.IOException e) {
                throw new MemberException("写真を添付できませんでした: " + e.getMessage());
            }
        }
        try {
            userActivityService.touchLastLogin(u, com.crm.entity.UserAccessLog.SOURCE_REPLY_SUBMIT, ip, ua, null);
        } catch (RuntimeException e) {
            log.debug("member send touch failed: {}", e.toString());
        }
        return saved;
    }

    /* ===================== 友達追加 / 検索 ===================== */

    public List<Chara> friends(CrmUser u) {
        return charaLinkService.linkedCharas(u.getId());
    }

    @Transactional
    public void addFriend(CrmUser u, Long charaId) {
        Chara c = chara(charaId).orElseThrow(() -> new MemberException("お相手が見つかりません"));
        charaLinkService.link(u.getId(), c.getId());
    }

    public boolean isFriend(CrmUser u, Long charaId) {
        for (Chara c : friends(u)) if (c.getId().equals(charaId)) return true;
        return false;
    }

    /** 条件検索 criteria (blank = 指定しない). */
    public static final class SearchInput {
        public String pref, photo, age, sign, blood;
    }

    /**
     * 条件検索 (uses 検索 points): キャラ of the other gender than the member (all when the member's
     * gender is unknown) matching every given condition, newest first.
     */
    @Transactional
    public List<Chara> search(CrmUser u, SearchInput in) {
        if (!userPointService.spend(u.getId(), cost(COST_SEARCH, u))) {
            throw new MemberException("ポイントが足りません（検索 " + cost(COST_SEARCH, u) + "pt）");
        }
        String wanted = "M".equals(u.getGender()) ? Chara.GENDER_FEMALE : "F".equals(u.getGender()) ? Chara.GENDER_MALE : null;
        int[] range = ageRange(in.age);
        List<Chara> out = new ArrayList<>();
        List<Chara> all = new ArrayList<>(charaRepository.findAllByOrderByIdAsc());
        Collections.reverse(all);
        for (Chara c : all) {
            if (wanted != null && !wanted.equals(c.getGender())) continue;
            if (!blank(in.pref) && !in.pref.equals(c.getPref())) continue;
            if ("yes".equals(in.photo) && c.getPhotoUrl() == null) continue;
            if ("no".equals(in.photo) && c.getPhotoUrl() != null) continue;
            if (range != null && (c.getAge() == null || c.getAge() < range[0] || c.getAge() > range[1])) continue;
            if (!blank(in.sign) && !in.sign.equals(c.getSign())) continue;
            if (!blank(in.blood) && !in.blood.equals(c.getBlood())) continue;
            out.add(c);
        }
        return out;
    }

    /** 年齢: "18-24" / "41-" → [min, max]; null = 指定しない. */
    static int[] ageRange(String v) {
        if (blank(v)) return null;
        String[] p = v.split("-", -1);
        try {
            int min = Integer.parseInt(p[0]);
            int max = p.length > 1 && !p[1].isEmpty() ? Integer.parseInt(p[1]) : 200;
            return new int[]{min, max};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /* ===================== サポート窓口 ===================== */

    @Transactional
    public void support(CrmUser u, String name, String email, String body) {
        String b = body == null ? "" : body.trim();
        if (b.isEmpty()) throw new MemberException("お問い合わせ内容を入力してください");
        if (b.length() > SupportDeskService.BODY_MAX) throw new MemberException("お問い合わせ内容が長すぎます");
        String mail = email == null ? "" : email.trim();
        if (mail.isEmpty()) mail = u.getEmail() == null ? "" : u.getEmail();
        if (mail.isEmpty()) throw new MemberException("メールアドレスを入力してください");
        if (mail.length() > 255 || !mail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new MemberException("メールアドレスの形式が正しくありません");
        String n = name == null || name.trim().isEmpty() ? u.getDisplayName() : name.trim();
        SupportInquiry q = new SupportInquiry();
        q.setChannel(SupportInquiry.CHANNEL_WEB);
        q.setName(n == null ? null : (n.length() > 255 ? n.substring(0, 255) : n));
        q.setEmail(mail);
        q.setSubject("会員ページ サポート窓口");
        q.setBody(b);
        q.setMemberId(u.getId());
        q.setFormPage("/member/support");
        supportInquiryRepository.save(q);
    }

    /* ===================== プロフ編集 ===================== */

    /** プロフィール入力. */
    public static final class ProfileInput {
        public String nickname, phone, pref, blood, sign, age, pr;
        public MultipartFile photo;
    }

    /** Saves プロフ編集 (プロフィール変更 points; a new 写真 also uses プロフィール写真変更 points). */
    @Transactional
    public void saveProfile(CrmUser u, ProfileInput in, String uploadedBy) {
        String nick = in.nickname == null ? "" : in.nickname.trim();
        if (nick.isEmpty()) throw new MemberException("ニックネームを入力してください");
        if (nick.length() > 20) throw new MemberException("ニックネームは20文字までです");
        String phone = in.phone == null ? "" : java.text.Normalizer.normalize(in.phone, java.text.Normalizer.Form.NFKC).replaceAll("[^0-9]", "");
        String current = u.getPhoneNumber() == null ? "" : u.getPhoneNumber().replaceAll("[^0-9]", "");
        boolean phoneChanged = !phone.equals(current);
        if (!phoneChanged) phone = u.getPhoneNumber() == null ? "" : u.getPhoneNumber();   // unchanged: kept as stored
        if (phoneChanged && !phone.isEmpty() && !phone.matches("0[789]0\\d{8}")) throw new MemberException("携帯番号は 090 / 080 / 070 から始まる11桁で入力してください");
        if (phoneChanged && !phone.isEmpty()) {
            Optional<CrmUser> other = Optional.empty();
            try {
                other = userRepository.findByPhoneNumber(phone);
            } catch (RuntimeException e) {
                other = Optional.of(u);   // several rows share it — treat as taken
            }
            if (other.isPresent() && !other.get().getId().equals(u.getId())) throw new MemberException("この携帯番号は登録できません");
        }
        boolean photo = in.photo != null && !in.photo.isEmpty();
        int total = cost(COST_PROFILE_EDIT, u) + (photo ? cost(COST_PROFILE_PHOTO, u) : 0);
        try {
            userProfileService.save(u.getId(), in.pref, in.blood, in.sign, in.age, in.pr);
        } catch (UserProfileService.ProfileException e) {
            throw new MemberException(e.getMessage());
        }
        if (!userPointService.spend(u.getId(), total)) {
            throw new MemberException("ポイントが足りません（必要 " + total + "pt）");
        }
        if (photo) {
            try {
                userProfileService.savePhoto(u.getId(), in.photo, uploadedBy);
            } catch (UserProfileService.ProfileException e) {
                throw new MemberException(e.getMessage());
            }
        }
        u.setDisplayName(nick);
        u.setPhoneNumber(phone.isEmpty() ? null : phone);
        userRepository.save(u);
    }

    /** A message's text as the member reads it: the 返信URL etc. taken out (they are on this page). */
    public static String displayBody(Message m) {
        String b = MessageBoxService.stripUrls(m.getBodyText());
        return b == null ? "" : b;
    }

    /* ===================== helpers ===================== */

    private Map<Long, Chara> charasById(Set<Long> ids) {
        Map<Long, Chara> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        for (Chara c : charaRepository.findAllById(ids)) out.put(c.getId(), c);
        return out;
    }

    private static boolean blank(String v) {
        return v == null || v.trim().isEmpty();
    }
}
