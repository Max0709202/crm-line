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
