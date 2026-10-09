package com.crm.service;

import com.crm.entity.CrmUser;
import com.crm.entity.MemberMemoSeen;
import com.crm.repository.MemberMemoSeenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * お知らせ — the member's 専用HTML (the 使用中 slot of 専用HTML 一括編集 / ユーザー詳細; none set = the
 * 返信画面設定's default HTML), shown at /member/memo. The LINE用 footer's お知らせ has a NEW mark
 * (like 受信BOX) until the member has viewed the 専用HTML they have now.
 */
@Service
public class MemberNoticeService {

    private final MemberMemoSeenRepository repository;
    private final ReplyPageSettingService replyPageSettingService;
    private final PlaceholderService placeholderService;

    public MemberNoticeService(MemberMemoSeenRepository repository, ReplyPageSettingService replyPageSettingService,
                               PlaceholderService placeholderService) {
        this.repository = repository;
        this.replyPageSettingService = replyPageSettingService;
        this.placeholderService = placeholderService;
    }

    /** The member's 専用HTML with their 置き換えタグ filled; null when there is none. */
    public String html(CrmUser u) {
        String raw = rawHtml(u);
        return raw == null ? null : placeholderService.substitute(raw, u);
    }

    /** NEW: there is a 専用HTML and the member hasn't viewed this one yet. */
    public boolean isNew(CrmUser u) {
        String raw = rawHtml(u);
        if (raw == null) return false;
        Optional<MemberMemoSeen> seen = repository.findById(u.getId());
        return !seen.isPresent() || !hash(raw).equals(seen.get().getSeenHash());
    }

    /** The member viewed their current 専用HTML (NEW goes away). */
    @Transactional
    public void markSeen(CrmUser u) {
        String raw = rawHtml(u);
        if (raw == null) return;
        MemberMemoSeen s = repository.findById(u.getId()).orElseGet(MemberMemoSeen::new);
        s.setUserId(u.getId());
        s.setSeenHash(hash(raw));
        s.setSeenAt(LocalDateTime.now());
        repository.save(s);
    }

    private String rawHtml(CrmUser u) {
        String memo = blankToNull(u.getActiveMemo());
        return memo != null ? memo : blankToNull(replyPageSettingService.getOrCreate().getDefaultHeaderHtml());
    }

    private static String hash(String html) {
        return DigestUtils.md5DigestAsHex(html.getBytes(StandardCharsets.UTF_8));
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s;
    }
}
