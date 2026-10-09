package com.crm.service;

import com.crm.entity.CrmUser;
import com.crm.entity.MemberAutoLogin;
import com.crm.repository.CrmUserRepository;
import com.crm.repository.MemberAutoLoginRepository;
import com.crm.util.TokenGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 自動ログインURL ({@code %auto_login_url%}) — a per-member link that logs the member in without
 * typing ID / password. Each member has one random 64-character token (created on first use);
 * only 本登録済み (ACTIVE) members can log in with it.
 */
@Service
public class MemberAutoLoginService {

    public static final String PATH = "/member/auto-login";

    /**
     * Pages the auto-login can open instead of the MENU ({@code &p=…}): the 置き換えタグ
     * %memo_url% (専用HTML・お知らせ), %inbox_url% (受信BOX) and %points_url% (ポイント購入・手続き).
     */
    public static final java.util.Map<String, String> PAGES;
    static {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("memo", "/member/memo");
        m.put("inbox", "/member/inbox");
        m.put("points", "/member/points");
        PAGES = java.util.Collections.unmodifiableMap(m);
    }

    /** 短縮URL: {@code /m/{token}} (MENU) or {@code /m/{token}/{page}}. */
    public static final String SHORT_PATH = "/m";

    private final MemberAutoLoginRepository repository;
    private final CrmUserRepository userRepository;
    private final DomainSettingService domainSettingService;

    public MemberAutoLoginService(MemberAutoLoginRepository repository, CrmUserRepository userRepository,
                                  DomainSettingService domainSettingService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.domainSettingService = domainSettingService;
    }

    /** The member's 自動ログインURL (absolute). */
    @Transactional
    public String urlFor(CrmUser user) {
        return baseUrl() + PATH + "?t=" + tokenFor(user.getId());
    }

    /** The member's 自動ログインURL that opens {@code page} (a key of {@link #PAGES}) after logging in. */
    @Transactional
    public String urlFor(CrmUser user, String page) {
        return urlFor(user) + (PAGES.containsKey(page) ? "&p=" + page : "");
    }

    /** Optional so hand-built instances (tests) work without it — there is then no 短縮URL. */
    private com.crm.repository.MemberShortLoginRepository shortRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setShortRepository(com.crm.repository.MemberShortLoginRepository shortRepository) { this.shortRepository = shortRepository; }

    /**
     * LINE設定 短縮URL: the member's 自動ログインURLs in {@code text} (as {@link #urlFor} wrote them, with or
     * without a page) become the short {@code /m/{token}/{page}} — the long one is cut off by LINE's
     * 本文の最大文字数 and too long to read. Text without them is returned as it is.
     */
    @Transactional
    public String shorten(String text, CrmUser user) {
        if (text == null || shortRepository == null || user == null || user.getId() == null) return text;
        Optional<MemberAutoLogin> a = repository.findById(user.getId());
        if (!a.isPresent()) return text;
        String longUrl = baseUrl() + PATH + "?t=" + a.get().getToken();
        if (!text.contains(longUrl)) return text;
        String shortUrl = baseUrl() + SHORT_PATH + "/" + shortTokenFor(user.getId());
        String out = text;
        for (String page : PAGES.keySet()) out = out.replace(longUrl + "&p=" + page, shortUrl + "/" + page);
        return out.replace(longUrl, shortUrl);
    }

    /** The ACTIVE member a 短縮URL token belongs to (as {@link #resolve}). */
    public Optional<CrmUser> resolveShort(String token) {
        if (shortRepository == null || token == null || token.length() != TokenGenerator.DEFAULT_SHORT_LENGTH) return Optional.empty();
        return shortRepository.findByToken(token)
                .flatMap(a -> userRepository.findById(a.getUserId()))
                .filter(u -> CrmUser.STATUS_ACTIVE.equals(u.getStatus()));
    }

    private String shortTokenFor(Long userId) {
        Optional<com.crm.entity.MemberShortLogin> existing = shortRepository.findById(userId);
        if (existing.isPresent()) return existing.get().getToken();
        com.crm.entity.MemberShortLogin a = new com.crm.entity.MemberShortLogin();
        a.setUserId(userId);
        a.setToken(TokenGenerator.generateShortReplyToken());
        return shortRepository.save(a).getToken();
    }

    /** Sample shown on メールテンプレート設定. */
    public String sampleUrl() {
        return baseUrl() + PATH + "?t=…";
    }

    /** The ACTIVE member the token belongs to; empty for an unknown token or a member not (yet / any more) active. */
    public Optional<CrmUser> resolve(String token) {
        if (token == null || token.length() != 64) return Optional.empty();
        return repository.findByToken(token)
                .flatMap(a -> userRepository.findById(a.getUserId()))
                .filter(u -> CrmUser.STATUS_ACTIVE.equals(u.getStatus()));
    }

    private String tokenFor(Long userId) {
        Optional<MemberAutoLogin> existing = repository.findById(userId);
        if (existing.isPresent()) return existing.get().getToken();
        MemberAutoLogin a = new MemberAutoLogin();
        a.setUserId(userId);
        a.setToken(TokenGenerator.generateReplyToken());
        return repository.save(a).getToken();
    }

    private String baseUrl() {
        String b = domainSettingService.getReplyBaseUrl();
        return b == null ? "" : b.trim().replaceAll("/+$", "");
    }
}
