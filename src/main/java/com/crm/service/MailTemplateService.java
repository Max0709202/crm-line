package com.crm.service;

import com.crm.entity.CrmSetting;
import com.crm.entity.CrmUser;
import com.crm.repository.CrmSettingRepository;
import com.crm.util.LogSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * サイト構成 › メールテンプレート設定 — the mails sent to members automatically (仮登録通知 / 本登録通知 /
 * 決済入金通知 / メール通知): subject, body and 有効・無効. Tags follow the 番組 (member site) spec:
 * the common ones are the member pages' {@code %sitename% %id% %name% %email% %point%} plus
 * {@code %login_url%} (ID・パスワードを入力するログイン) and {@code %auto_login_url%} (その会員として自動ログイン). 決済入金通知's tags are {@code %pay_…%} (入金履歴's 金額・pt・方法・入金日時) so they
 * never clash with ユーザー詳細's 置き換えタグ such as {@code %amount%}.
 * A template sends only while 有効 and with a subject and body; 仮登録通知 falls back to the
 * built-in text while unwritten, since without it nobody can finish registering.
 */
@Service
public class MailTemplateService {

    private static final Logger log = LoggerFactory.getLogger(MailTemplateService.class);

    public static final String PROVISIONAL = "provisional";
    public static final String REGISTERED = "registered";
    public static final String PAYMENT = "payment";
    public static final String NOTICE = "notice";

    public static final int MAX_SUBJECT = 100;
    public static final int MAX_BODY = 10000;

    private static final String PREFIX = "mail_tpl.";
    private static final DateTimeFormatter JP_DATE = DateTimeFormatter.ofPattern("yyyy年M月d日");
    private static final DateTimeFormatter JP_DATETIME = DateTimeFormatter.ofPattern("yyyy年M月d日 H:mm");
    private static final Pattern TOKEN = Pattern.compile("%[a-z_]+%");
    /** 決済入金通知 tags before they were renamed to %pay_…% (2026-10-05) → current name. */
    private static final Map<String, String> RENAMED_PAYMENT_TAGS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("%amount%", "%pay_amount%");
        m.put("%add_point%", "%pay_point%");
        m.put("%payment_method%", "%pay_method%");
        m.put("%paid_at%", "%pay_date%");
        m.put("%order_no%", "%pay_no%");
        RENAMED_PAYMENT_TAGS = Collections.unmodifiableMap(m);
    }

    /** One template: key, name, timing (送るタイミング). */
    public static final class Def {
        public final String key;
        public final String name;
        public final String timing;
        Def(String key, String name, String timing) { this.key = key; this.name = name; this.timing = timing; }
    }

    public static final List<Def> DEFS = Collections.unmodifiableList(Arrays.asList(
            new Def(PROVISIONAL, "仮登録通知", "ユーザーがメールアドレスを登録したとき"),
            new Def(REGISTERED, "本登録通知", "本登録用URLから登録が完了したとき"),
            new Def(PAYMENT, "決済入金通知", "決済・入金が確認されたとき"),
            new Def(NOTICE, "メール通知", "メッセージが届いたとき（新着のお知らせ）")));

    /** 仮登録通知 sent while the template is unwritten — the text used before this page existed. */
    static final String DEFAULT_PROVISIONAL_SUBJECT = "【%sitename%】本登録のご案内";
    static final String DEFAULT_PROVISIONAL_BODY = "%sitename%へのご登録ありがとうございます。\n"
            + "\n"
            + "下記のURLをクリックして、本登録を完了してください。\n"
            + "%verify_url%\n"
            + "\n"
            + "ログインID：%id%\n"
            + "パスワード：ご登録時に入力されたパスワード\n"
            + "\n"
            + "※URLの有効期限は%expire%までです。期限が切れた場合は、もう一度ご登録ください。\n"
            + "※このメールにお心当たりがない場合は、お手数ですが破棄してください。\n";

    public static class TemplateException extends RuntimeException {
        public TemplateException(String msg) { super(msg); }
    }

    private final CrmSettingRepository repository;
    private final SiteDesignService siteDesignService;
    private final DomainSettingService domainSettingService;
    private final UserPointService userPointService;
    private final LocalPostfixOutboundMailService mailService;
    private final MemberAutoLoginService autoLoginService;

    public MailTemplateService(CrmSettingRepository repository, SiteDesignService siteDesignService,
                               DomainSettingService domainSettingService, UserPointService userPointService,
                               LocalPostfixOutboundMailService mailService, MemberAutoLoginService autoLoginService) {
        this.repository = repository;
        this.siteDesignService = siteDesignService;
        this.domainSettingService = domainSettingService;
        this.userPointService = userPointService;
        this.mailService = mailService;
        this.autoLoginService = autoLoginService;
    }

    /* ===================== 設定画面 ===================== */

    /** Tags per template for the page: "common" + each key → [タグ, 説明, サンプル値]. */
    public Map<String, List<String[]>> tags() {
        String base = baseUrl();
        String site = siteDesignService.getSiteName();
        Map<String, List<String[]>> m = new LinkedHashMap<>();
        m.put("common", Arrays.asList(
                new String[]{"%sitename%", "サイト名", site.isEmpty() ? "サイト名" : site},
                new String[]{"%id%", "会員ID（ログインID）", "10054"},
                new String[]{"%name%", "表示名（ニックネーム）", "なおと"},
                new String[]{"%email%", "メールアドレス", "naoto.k@example.com"},
                new String[]{"%point%", "所持ポイント", "1,200"},
                new String[]{"%login_url%", "ログインURL（ID・パスワード入力）", loginUrl()},
                new String[]{"%auto_login_url%", "自動ログインURL（ID・パスワード入力なし）", autoLoginService.sampleUrl()}));
        m.put(PROVISIONAL, Arrays.asList(
                new String[]{"%verify_url%", "本登録用URL", base + "/member/confirm?token=3f9a…"},
                new String[]{"%expire%", "URLの有効期限", LocalDateTime.now().plusHours(MemberRegistrationService.TOKEN_VALID_HOURS).format(JP_DATETIME)}));
        m.put(REGISTERED, Collections.singletonList(
                new String[]{"%profile_url%", "プロフィール編集画面", base + PublicSiteService.PROFILE_URL}));
        m.put(PAYMENT, Arrays.asList(
                new String[]{"%pay_amount%", "入金額（金額）", "5,000円"},
                new String[]{"%pay_point%", "付与ポイント（pt）", "500pt"},
                new String[]{"%pay_method%", "支払方法（方法）", "コンビニ決済"},
                new String[]{"%pay_date%", "入金日時", LocalDateTime.now().format(JP_DATETIME)},
                new String[]{"%pay_no%", "入金番号", "102938"}));
        m.put(NOTICE, Arrays.asList(
                new String[]{"%staff_name%", "送信者（キャラ）名", "サポートA"},
                new String[]{"%message_title%", "メッセージの件名", "ご注文の件について"},
                new String[]{"%reply_url%", "返信画面", base + "/reply/…"}));
        return m;
    }

    /** The templates for the page: key, name, timing, enabled, subject, body, updatedAt. */
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Def d : DEFS) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("key", d.key);
            t.put("name", d.name);
            t.put("timing", d.timing);
            t.put("enabled", isEnabled(d.key));
            t.put("subject", subject(d.key));
            t.put("body", body(d.key));
            String at = get(PREFIX + d.key + ".updated_at");
            t.put("updatedAt", at == null ? "" : at);
            out.add(t);
        }
        return out;
    }

    /** @return updatedAt (yyyy-MM-ddTHH:mm) */
    @Transactional
    public String save(String key, String subject, String body) {
        def(key);
        String s = subject == null ? "" : subject.trim();
        String b = body == null ? "" : body.replace("\r\n", "\n");
        if (s.isEmpty()) throw new TemplateException("件名を入力してください");
        if (b.trim().isEmpty()) throw new TemplateException("本文を入力してください");
        if (s.length() > MAX_SUBJECT) throw new TemplateException("件名は" + MAX_SUBJECT + "文字までです");
        if (b.length() > MAX_BODY) throw new TemplateException("本文は" + MAX_BODY + "文字までです");
        if (s.contains("\n")) throw new TemplateException("件名に改行は使えません");
        put(PREFIX + key + ".subject", s);
        put(PREFIX + key + ".body", b);
        String at = RelayRoutingService.isoMinutes(LocalDateTime.now());
        put(PREFIX + key + ".updated_at", at);
        return at;
    }

    @Transactional
    public void setEnabled(String key, boolean enabled) {
        def(key);
        put(PREFIX + key + ".enabled", String.valueOf(enabled));
    }

    /** テスト送信: the given subject/body with the tags' sample values, to {@code to}. */
    public void sendTest(String key, String to, String subject, String body) {
        def(key);
        String addr = to == null ? "" : to.trim();
        if (!addr.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new TemplateException("テスト送信先のメールアドレスを入力してください");
        if (subject == null || subject.trim().isEmpty() || body == null || body.trim().isEmpty()) {
            throw new TemplateException("件名と本文を入力してください");
        }
        Map<String, String> sample = new LinkedHashMap<>();
        for (String[] t : tags().get("common")) sample.put(t[0], t[2]);
        for (String[] t : tags().get(key)) sample.put(t[0], t[2]);
        OutboundMailService.SendResult res = deliver(addr, fill(subject.trim(), sample), fill(body, sample));
        if (res == null) throw new TemplateException("送信元アドレスがありません（ドメイン設定・お問い合わせアドレスを確認してください）");
        if (!res.success) throw new TemplateException("送信に失敗しました: " + res.errorMessage);
    }

    /* ===================== 送信 ===================== */

    public boolean isEnabled(String key) {
        String v = get(PREFIX + key + ".enabled");
        return v == null || "true".equalsIgnoreCase(v.trim());
    }

    /**
     * Sends template {@code key} to the member with the common tags plus {@code extra}. Skipped while
     * 無効 or unwritten; never throws — a mail problem must not break registration or a 入金.
     *
     * @return true when the mail was handed to the mail server
     */
    public boolean send(String key, CrmUser user, Map<String, String> extra) {
        try {
            if (user == null || user.getEmail() == null || user.getEmail().trim().isEmpty() || !isEnabled(key)) return false;
            String subject = subject(key), body = body(key);
            if (PROVISIONAL.equals(key) && (subject.isEmpty() || body.isEmpty())) {
                subject = DEFAULT_PROVISIONAL_SUBJECT;
                body = DEFAULT_PROVISIONAL_BODY;
            }
            if (subject.isEmpty() || body.trim().isEmpty()) return false;
            Map<String, String> values = new LinkedHashMap<>();
            values.put("%sitename%", siteDesignService.getSiteName());
            values.put("%id%", nz(user.getLoginId()));
            values.put("%name%", nz(user.getDisplayName()));
            values.put("%email%", nz(user.getEmail()));
            values.put("%point%", String.format("%,d", userPointService.get(user.getId())));
            values.put("%login_url%", loginUrl());
            if (containsTag(subject, body, "%auto_login_url%")) values.put("%auto_login_url%", autoLoginService.urlFor(user));
            if (extra != null) values.putAll(extra);
            OutboundMailService.SendResult res = deliver(user.getEmail().trim(), fill(subject, values), fill(body, values));
            if (res == null) {
                log.warn("mail template {} not sent (no main domain / お問い合わせ address): user={}", key, user.getId());
                return false;
            }
            if (!res.success) {
                log.warn("mail template {} failed: user={} error={}", key, user.getId(), LogSafe.of(res.errorMessage));
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            log.warn("mail template {} error: user={} {}", key, user == null ? null : user.getId(), e.toString());
            return false;
        }
    }

    /** 仮登録通知 — {@code %verify_url%} / {@code %expire%}. */
    public boolean sendProvisional(CrmUser user, String verifyUrl, LocalDateTime expiresAt) {
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("%verify_url%", verifyUrl);
        extra.put("%expire%", expiresAt == null ? "" : expiresAt.format(JP_DATETIME));
        return send(PROVISIONAL, user, extra);
    }

    /** 本登録通知 — {@code %profile_url%}. */
    public boolean sendRegistered(CrmUser user) {
        return send(REGISTERED, user, Collections.singletonMap("%profile_url%", baseUrl() + PublicSiteService.PROFILE_URL));
    }

    /** 決済入金通知. */
    public boolean sendPayment(CrmUser user, java.math.BigDecimal amount, int addPoints, String methodLabel,
                               LocalDateTime paidAt, Long paymentId) {
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("%pay_amount%", amount == null ? "" : String.format("%,d円", amount.longValue()));
        extra.put("%pay_point%", String.format("%,dpt", addPoints));
        extra.put("%pay_method%", nz(methodLabel));
        extra.put("%pay_date%", paidAt == null ? "" : paidAt.format(JP_DATETIME));
        extra.put("%pay_no%", paymentId == null ? "" : String.valueOf(paymentId));
        return send(PAYMENT, user, extra);
    }

    /**
     * 決済入金通知 saved with the old tag names (%amount% etc.) is rewritten to the %pay_…% names
     * once at startup, so a template saved before the rename keeps working. Idempotent.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @Transactional
    public void migrateRenamedPaymentTags() {
        for (String part : Arrays.asList("subject", "body")) {
            String key = PREFIX + PAYMENT + "." + part;
            String v = get(key);
            if (v == null || v.isEmpty()) continue;
            String nv = renamePaymentTags(v);
            if (!nv.equals(v)) {
                put(key, nv);
                log.info("mail template {}: renamed payment tags to %pay_…%", key);
            }
        }
    }

    static String renamePaymentTags(String text) {
        Matcher m = TOKEN.matcher(text);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String renamed = RENAMED_PAYMENT_TAGS.get(m.group());
            m.appendReplacement(out, Matcher.quoteReplacement(renamed == null ? m.group() : renamed));
        }
        m.appendTail(out);
        return out.toString();
    }

    /* ===================== helpers ===================== */

    private static boolean containsTag(String subject, String body, String tag) {
        return subject.contains(tag) || body.contains(tag);
    }

    /** %login_url% — the member site's top page with the ログイン dialog open. */
    private String loginUrl() {
        return baseUrl() + "/#login";
    }

    private OutboundMailService.SendResult deliver(String to, String subject, String body) {
        String from = siteDesignService.getContactEmail();
        if (from == null) return null;
        return mailService.send(new OutboundMailService.OutboundRequest(from, to, subject, body, "127.0.0.1", 25, null, null));
    }

    /** Known tags → values; unknown %tags% are left as written. */
    static String fill(String text, Map<String, String> values) {
        Matcher m = TOKEN.matcher(text);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String v = values.get(m.group());
            if (v == null && "%date_jp%".equals(m.group())) v = LocalDate.now().format(JP_DATE);
            m.appendReplacement(out, Matcher.quoteReplacement(v == null ? m.group() : v));
        }
        m.appendTail(out);
        return out.toString();
    }

    private String subject(String key) {
        String v = get(PREFIX + key + ".subject");
        if ((v == null || v.isEmpty()) && PROVISIONAL.equals(key)) return DEFAULT_PROVISIONAL_SUBJECT;
        return v == null ? "" : v;
    }

    private String body(String key) {
        String v = get(PREFIX + key + ".body");
        if ((v == null || v.isEmpty()) && PROVISIONAL.equals(key)) return DEFAULT_PROVISIONAL_BODY;
        return v == null ? "" : v;
    }

    private String baseUrl() {
        String b = domainSettingService.getReplyBaseUrl();
        return b == null ? "" : b.trim().replaceAll("/+$", "");
    }

    private static Def def(String key) {
        for (Def d : DEFS) if (d.key.equals(key)) return d;
        throw new TemplateException("テンプレートが見つかりません");
    }

    private static String nz(String v) { return v == null ? "" : v; }

    private String get(String key) {
        return repository.findBySettingKey(key).map(CrmSetting::getSettingValue).orElse(null);
    }

    private void put(String key, String value) {
        CrmSetting s = repository.findBySettingKey(key).orElseGet(() -> {
            CrmSetting n = new CrmSetting();
            n.setSettingKey(key);
            return n;
        });
        s.setSettingValue(value);
        repository.save(s);
    }
}
