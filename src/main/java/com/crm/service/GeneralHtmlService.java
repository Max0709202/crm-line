package com.crm.service;

import com.crm.entity.CrmSetting;
import com.crm.entity.CrmUser;
import com.crm.entity.GeneralHtml;
import com.crm.repository.CrmSettingRepository;
import com.crm.repository.CrmUserRepository;
import com.crm.repository.GeneralHtmlRepository;
import com.crm.repository.UserAccessLogRepository;
import com.crm.util.TokenGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全般HTML (2026-10-18 client request, HTML 一括編集 page): SLOT_COUNT HTML pages that are not per
 * user like the 専用HTML — each has its own generated URL ({@code /h/{urlKey}}) that anyone can open,
 * whatever their folder, without logging in.
 *
 * <p>アクセスユーザー: a view is recorded for the user it can be tied to — the personalised link a
 * sent message carries (the plain URL pasted into a message body becomes {@code /h/{key}?u=…} per
 * recipient, see {@link #personalize}), or a logged-in member. Anonymous views are shown but not logged.
 * Rows go to USER_ACCESS_LOG with SOURCE {@code GENERAL_HTML_<slot>}, like 外部リンクドメイン's.
 */
@Service
public class GeneralHtmlService {

    public static final int SLOT_COUNT = 30;
    public static final String PATH = "/h";
    private static final int KEY_LENGTH = 12;
    private static final String SOURCE_PREFIX = "GENERAL_HTML_";
    private static final String KEY_SECRET = "general_html.view_secret";

    /** An absolute 全般HTML URL not yet personalised (no query string after the key). */
    private static final Pattern URL_IN_TEXT = Pattern.compile(
            "(https?://[^\\s\"'<>]+?" + PATH + "/)([A-Za-z0-9]{" + KEY_LENGTH + "})(?![A-Za-z0-9?])");

    private final GeneralHtmlRepository repository;
    private final UserAccessLogRepository accessLogRepository;
    private final CrmUserRepository userRepository;
    private final CrmSettingRepository settingRepository;
    private final DomainSettingService domainSettingService;

    /** URL keys of every slot — read by {@link #personalize} for each sent message, so cached. */
    private volatile Set<String> knownKeys;
    private volatile byte[] secret;

    public GeneralHtmlService(GeneralHtmlRepository repository, UserAccessLogRepository accessLogRepository,
                              CrmUserRepository userRepository, CrmSettingRepository settingRepository,
                              DomainSettingService domainSettingService) {
        this.repository = repository;
        this.accessLogRepository = accessLogRepository;
        this.userRepository = userRepository;
        this.settingRepository = settingRepository;
        this.domainSettingService = domainSettingService;
    }

    /** All SLOT_COUNT slots in order; a slot never saved gets its row (and URL) here. */
    @Transactional
    public List<GeneralHtml> listSlots() {
        Map<Integer, GeneralHtml> bySlot = new java.util.HashMap<>();
        for (GeneralHtml g : repository.findAllByOrderBySlotNoAsc()) bySlot.put(g.getSlotNo(), g);
        List<GeneralHtml> out = new ArrayList<>(SLOT_COUNT);
        boolean created = false;
        for (int s = 1; s <= SLOT_COUNT; s++) {
            GeneralHtml g = bySlot.get(s);
            if (g == null) {
                g = new GeneralHtml();
                g.setSlotNo(s);
                g.setUrlKey(TokenGenerator.generateShortReplyToken(KEY_LENGTH));
                g.setUpdatedAt(LocalDateTime.now());
                g = repository.save(g);
                created = true;
            }
            out.add(g);
        }
        if (created) knownKeys = null;
        return out;
    }

    /** Saves every slot's タイトル and HTML ({@code titleN} / {@code htmlN}; a slot not sent is left as is). */
    @Transactional
    public void saveAll(Map<String, String> params) {
        for (GeneralHtml g : listSlots()) {
            int s = g.getSlotNo();
            if (!params.containsKey("html" + s) && !params.containsKey("title" + s)) continue;
            String title = params.get("title" + s);
            String html = params.get("html" + s);
            title = title == null ? null : title.trim();
            g.setTitle(title == null || title.isEmpty() ? null : (title.length() > 100 ? title.substring(0, 100) : title));
            g.setHtml(html == null || html.trim().isEmpty() ? null : html);
            g.setUpdatedAt(LocalDateTime.now());
            repository.save(g);
        }
    }

    public Optional<GeneralHtml> findByKey(String urlKey) {
        if (urlKey == null || urlKey.length() != KEY_LENGTH) return Optional.empty();
        return repository.findByUrlKey(urlKey);
    }

    public Optional<GeneralHtml> findBySlot(int slotNo) {
        return repository.findById(slotNo);
    }

    /** The slot's public URL (absolute, on the 返信URL domain). */
    public String urlFor(GeneralHtml g) {
        String b = domainSettingService.getReplyBaseUrl();
        return (b == null ? "" : b.trim().replaceAll("/+$", "")) + PATH + "/" + g.getUrlKey();
    }

    /** USER_ACCESS_LOG.SOURCE of a view of slot {@code slotNo}. */
    public static String source(int slotNo) {
        return SOURCE_PREFIX + slotNo;
    }

    /** アクセス数: distinct users who viewed the slot. */
    public long countAccessUsers(int slotNo) {
        return accessLogRepository.countDistinctUserIdsBySource(source(slotNo));
    }

    /** アクセスユーザー, sorted like 外部リンクドメイン's (most recent 最終ログイン first). */
    public List<ExternalLinkDomainService.AccessedUser> listAccessUsers(int slotNo) {
        List<ExternalLinkDomainService.AccessedUser> out = new ArrayList<>();
        for (Long userId : accessLogRepository.findDistinctUserIdsBySource(source(slotNo))) {
            userRepository.findById(userId).ifPresent(u -> out.add(new ExternalLinkDomainService.AccessedUser(u)));
        }
        out.sort((a, b) -> {
            LocalDateTime la = a.getLastLoginAt();
            LocalDateTime lb = b.getLastLoginAt();
            if (la == null && lb == null) return 0;
            if (la == null) return 1;
            if (lb == null) return -1;
            return lb.compareTo(la);
        });
        return out;
    }

    /** アクセス履歴削除: every recorded view of the slot (アクセス数 back to 0). */
    @Transactional
    public int deleteAccessHistory(int slotNo) {
        return accessLogRepository.deleteBySource(source(slotNo));
    }

    /**
     * Each 全般HTML URL in {@code text} (a message body / HTML for {@code user}) gets the user's
     * {@code ?u=} so the view is recorded as theirs. Text without one is returned as it is.
     */
    public String personalize(String text, CrmUser user) {
        if (text == null || user == null || user.getId() == null || !text.contains(PATH + "/")) return text;
        Set<String> keys = keys();
        Matcher m = URL_IN_TEXT.matcher(text);
        StringBuffer out = new StringBuffer();
        boolean any = false;
        while (m.find()) {
            if (!keys.contains(m.group(2))) continue;
            m.appendReplacement(out, Matcher.quoteReplacement(m.group() + "?u=" + viewerParam(user.getId())));
            any = true;
        }
        if (!any) return text;
        m.appendTail(out);
        return out.toString();
    }

    /** The user a {@code ?u=} value was made for; empty when it is missing or forged. */
    public Optional<CrmUser> resolveViewer(String u) {
        if (u == null) return Optional.empty();
        int dot = u.indexOf('.');
        if (dot <= 0) return Optional.empty();
        Long userId;
        try {
            userId = Long.valueOf(u.substring(0, dot));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (!viewerParam(userId).equals(u)) return Optional.empty();
        return userRepository.findById(userId);
    }

    /** {@code <userId>.<signature>} — identifies the viewer without being a login credential. */
    private String viewerParam(Long userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret(), "HmacSHA256"));
            byte[] sig = mac.doFinal(String.valueOf(userId).getBytes(StandardCharsets.UTF_8));
            return userId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig).substring(0, 16);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private Set<String> keys() {
        Set<String> k = knownKeys;
        if (k == null) {
            k = new HashSet<>();
            for (GeneralHtml g : repository.findAll()) k.add(g.getUrlKey());
            knownKeys = k;
        }
        return k;
    }

    private synchronized byte[] secret() {
        if (secret != null) return secret;
        String v = settingRepository.findBySettingKey(KEY_SECRET).map(CrmSetting::getSettingValue).orElse(null);
        if (v == null || v.isEmpty()) {
            v = TokenGenerator.generateReplyToken();
            CrmSetting s = new CrmSetting();
            s.setSettingKey(KEY_SECRET);
            s.setSettingValue(v);
            s.setDescription("全般HTML アクセスユーザー: 閲覧URLの ?u= 署名キー");
            s.setUpdatedAt(LocalDateTime.now());
            settingRepository.save(s);
        }
        secret = v.getBytes(StandardCharsets.UTF_8);
        return secret;
    }
}
