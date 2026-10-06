package com.crm.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Things a member has paid ポイント to see (ポイント設定: 本文閲覧 / プロフィール閲覧 / 写真閲覧): the first
 * look costs the set points, later looks are free. MEMBER_UNLOCK holds one row per member × thing.
 */
@Service
public class MemberUnlockService {

    /** 本文閲覧 — ref = MESSAGE.ID. */
    public static final String BODY = "BODY";
    /** プロフィール閲覧 — ref = CHARA.ID. */
    public static final String PROFILE = "PROFILE";
    /** 写真閲覧 (キャラの写真) — ref = CHARA.ID. */
    public static final String PHOTO = "PHOTO";
    /** 写真閲覧 (メッセージの添付画像) — ref = HTML_IMAGE.ID. */
    public static final String IMAGE = "IMAGE";

    public enum Result { OK, NOT_ENOUGH_POINTS }

    private final JdbcTemplate jdbc;
    private final UserPointService userPointService;

    public MemberUnlockService(JdbcTemplate jdbc, UserPointService userPointService) {
        this.jdbc = jdbc;
        this.userPointService = userPointService;
    }

    public boolean isUnlocked(Long userId, String kind, Long refId) {
        if (userId == null || refId == null) return false;
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM MEMBER_UNLOCK WHERE USER_ID = ? AND KIND = ? AND REF_ID = ?",
                Integer.class, userId, kind, refId);
        return n != null && n > 0;
    }

    /** The given refs the member has unlocked. */
    public Set<Long> unlocked(Long userId, String kind, Collection<Long> refIds) {
        Set<Long> out = new HashSet<>();
        if (userId == null || refIds == null || refIds.isEmpty()) return out;
        StringBuilder in = new StringBuilder();
        Object[] args = new Object[refIds.size() + 2];
        args[0] = userId;
        args[1] = kind;
        int i = 2;
        for (Long id : refIds) {
            if (in.length() > 0) in.append(',');
            in.append('?');
            args[i++] = id;
        }
        jdbc.query("SELECT REF_ID FROM MEMBER_UNLOCK WHERE USER_ID = ? AND KIND = ? AND REF_ID IN (" + in + ")",
                rs -> { out.add(rs.getLong(1)); }, args);
        return out;
    }

    /**
     * Unlocks one thing: free when already unlocked; else uses {@code cost} points (0 = free, still
     * recorded so e.g. the message stops being 未読). The row is written first (unique key), so two
     * clicks at once can't charge twice.
     */
    @Transactional
    public Result unlock(Long userId, String kind, Long refId, int cost) {
        int inserted = jdbc.update("INSERT IGNORE INTO MEMBER_UNLOCK (USER_ID, KIND, REF_ID, POINTS, CREATED_AT) VALUES (?, ?, ?, ?, NOW())",
                userId, kind, refId, Math.max(0, cost));
        if (inserted == 0) return Result.OK;   // already unlocked
        if (!userPointService.spend(userId, cost)) {
            jdbc.update("DELETE FROM MEMBER_UNLOCK WHERE USER_ID = ? AND KIND = ? AND REF_ID = ?", userId, kind, refId);
            return Result.NOT_ENOUGH_POINTS;
        }
        return Result.OK;
    }
}
