package com.crm.service;

import com.crm.entity.UserPoint;
import com.crm.repository.UserPointRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 所持ポイント (USER_POINT; a member without a row has 0 pt). Shown in the ユーザー管理 list,
 * changed in bulk by user-ID range on ポイント設定, and granted the 初期ポイント at member
 * registration.
 */
@Service
public class UserPointService {

    public static final int MAX_POINTS = 9_999_999;

    public enum BulkMode { SET, ADD, SUBTRACT }

    private final UserPointRepository repository;
    private final JdbcTemplate jdbc;

    public UserPointService(UserPointRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    public int get(Long userId) {
        if (userId == null) return 0;
        return repository.findById(userId).map(UserPoint::getPoints).orElse(0);
    }

    /** Points of each given user (users without a row are absent → treat as 0). */
    public Map<Long, Integer> getAll(Collection<Long> userIds) {
        Map<Long, Integer> out = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) return out;
        for (UserPoint p : repository.findAllById(userIds)) out.put(p.getUserId(), p.getPoints());
        return out;
    }

    @Transactional
    public void set(Long userId, int points) {
        UserPoint p = repository.findById(userId).orElseGet(() -> {
            UserPoint n = new UserPoint();
            n.setUserId(userId);
            return n;
        });
        p.setPoints(clamp(points));
        repository.save(p);
    }

    /**
     * ポイント設定 › 所持ポイント一括変更: every existing user with {@code fromId <= ID <= toId}
     * gets {@code amount} set / added / subtracted (never below 0, never above {@link #MAX_POINTS}).
     *
     * @return the number of users changed
     */
    @Transactional
    public int bulkChange(long fromId, long toId, BulkMode mode, int amount) {
        if (fromId > toId) { long t = fromId; fromId = toId; toId = t; }
        int a = clamp(amount);
        String select;
        String onDup;
        Object[] args;
        switch (mode) {
            case SET:
                select = "?";
                onDup = "POINTS = VALUES(POINTS)";
                args = new Object[]{a, fromId, toId};
                break;
            case ADD:
                select = "LEAST(?, " + MAX_POINTS + ")";
                onDup = "POINTS = LEAST(POINTS + " + a + ", " + MAX_POINTS + ")";
                args = new Object[]{a, fromId, toId};
                break;
            default:   // SUBTRACT — a user without a row has 0 and stays at 0
                select = "0";
                onDup = "POINTS = GREATEST(POINTS - " + a + ", 0)";
                args = new Object[]{fromId, toId};
        }
        Integer users = jdbc.queryForObject("SELECT COUNT(*) FROM CRM_USER WHERE ID BETWEEN ? AND ?",
                Integer.class, fromId, toId);
        jdbc.update("INSERT INTO USER_POINT (USER_ID, POINTS, UPDATED_AT) "
                + "SELECT ID, " + select + ", NOW() FROM CRM_USER WHERE ID BETWEEN ? AND ? "
                + "ON DUPLICATE KEY UPDATE " + onDup + ", UPDATED_AT = NOW()", args);
        return users == null ? 0 : users;
    }

    /** Adds {@code points} (≥ 0) to the member's 所持ポイント in one statement (決済 / 入金). */
    @Transactional
    public void add(Long userId, int points) {
        if (userId == null || points <= 0) return;
        int a = clamp(points);
        jdbc.update("INSERT INTO USER_POINT (USER_ID, POINTS, UPDATED_AT) VALUES (?, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE POINTS = LEAST(POINTS + ?, " + MAX_POINTS + "), UPDATED_AT = NOW()",
                userId, a, a);
    }

    /**
     * Uses {@code cost} pt of the member's 所持ポイント (会員ページ: 写真閲覧・メール送信 …) in one
     * statement, so two clicks can't spend the same points twice.
     *
     * @return true when the member had enough (cost 0 always succeeds), false otherwise (nothing used)
     */
    @Transactional
    public boolean spend(Long userId, int cost) {
        if (userId == null) return false;
        if (cost <= 0) return true;
        return jdbc.update("UPDATE USER_POINT SET POINTS = POINTS - ?, UPDATED_AT = NOW() WHERE USER_ID = ? AND POINTS >= ?",
                cost, userId, cost) == 1;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(MAX_POINTS, v));
    }
}
