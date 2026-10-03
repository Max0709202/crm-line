package com.crm.service;

import com.crm.entity.CrmSetting;
import com.crm.repository.CrmSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 受信ボックス (message/thread) 4-pane layout saved by 「画面レイアウト保存」 — client request 2026-10-02.
 * One CRM_SETTING row per admin, value = "wT,wB,hL,hR[,memoMember,memoStaff,cards]": the top /
 * bottom vertical sash positions (% of width), the left / right horizontal sash positions
 * (% of height) and — since 2026-10-03, so the save covers the whole screen — the やり取りメモ
 * heights in px (0 = default) and whether the user / キャラ cards are open (1) or closed (0).
 */
@Service
public class ThreadLayoutService {

    private static final String KEY_PREFIX = "thread.layout.admin.";
    public static final double MIN_PCT = 10;
    public static final double MAX_PCT = 90;
    public static final int MEMO_MAX_PX = 1000;

    private final CrmSettingRepository repo;

    public ThreadLayoutService(CrmSettingRepository repo) { this.repo = repo; }

    /** Saved "wT,wB,hL,hR,memoMember,memoStaff,cards" for this admin, or null when nothing (valid) is saved. */
    public String get(Long adminId) {
        if (adminId == null) return null;
        String raw = repo.findBySettingKey(KEY_PREFIX + adminId).map(CrmSetting::getSettingValue).orElse(null);
        double[] v = parse(raw);
        return v == null ? null : format(v);
    }

    /** Sashes only (memo heights default, cards open). */
    @Transactional
    public boolean save(Long adminId, double wT, double wB, double hL, double hR) {
        return save(adminId, wT, wB, hL, hR, 0, 0, true);
    }

    /** Validates and stores the layout; returns false if any value is out of range. */
    @Transactional
    public boolean save(Long adminId, double wT, double wB, double hL, double hR,
                        int memoMemberPx, int memoStaffPx, boolean cardsOpen) {
        if (adminId == null) return false;
        double[] v = {wT, wB, hL, hR, memoMemberPx, memoStaffPx, cardsOpen ? 1 : 0};
        if (!valid(v)) return false;
        CrmSetting s = repo.findBySettingKey(KEY_PREFIX + adminId).orElseGet(() -> {
            CrmSetting ns = new CrmSetting();
            ns.setSettingKey(KEY_PREFIX + adminId);
            ns.setDescription("受信ボックス 画面レイアウト (wT,wB,hL,hR %)");
            return ns;
        });
        s.setSettingValue(format(v));
        s.setUpdatedAt(LocalDateTime.now());
        repo.save(s);
        return true;
    }

    private static double[] parse(String raw) {
        if (raw == null) return null;
        String[] parts = raw.split(",");
        if (parts.length != 4 && parts.length != 7) return null;
        double[] v = {0, 0, 0, 0, 0, 0, 1};
        try {
            for (int i = 0; i < parts.length; i++) v[i] = Double.parseDouble(parts[i].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        return valid(v) ? v : null;
    }

    private static boolean valid(double[] v) {
        for (int i = 0; i < 4; i++) if (!(v[i] >= MIN_PCT && v[i] <= MAX_PCT)) return false;
        for (int i = 4; i < 6; i++) if (!(v[i] >= 0 && v[i] <= MEMO_MAX_PX)) return false;
        return v[6] == 0 || v[6] == 1;
    }

    private static String format(double[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            if (i < 4) sb.append(Math.round(v[i] * 10) / 10.0);
            else sb.append(Math.round(v[i]));
        }
        return sb.toString();
    }
}
