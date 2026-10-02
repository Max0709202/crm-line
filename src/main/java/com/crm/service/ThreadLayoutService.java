package com.crm.service;

import com.crm.entity.CrmSetting;
import com.crm.repository.CrmSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 受信ボックス (message/thread) 4-pane layout saved by 「画面レイアウト保存」 — client request 2026-10-02.
 * One CRM_SETTING row per admin, value = "wT,wB,hL,hR": the top / bottom vertical sash
 * positions (% of width) and the left / right horizontal sash positions (% of height).
 */
@Service
public class ThreadLayoutService {

    private static final String KEY_PREFIX = "thread.layout.admin.";
    public static final double MIN_PCT = 10;
    public static final double MAX_PCT = 90;

    private final CrmSettingRepository repo;

    public ThreadLayoutService(CrmSettingRepository repo) { this.repo = repo; }

    /** Saved "wT,wB,hL,hR" for this admin, or null when nothing (valid) is saved. */
    public String get(Long adminId) {
        if (adminId == null) return null;
        String raw = repo.findBySettingKey(KEY_PREFIX + adminId).map(CrmSetting::getSettingValue).orElse(null);
        double[] v = parse(raw);
        return v == null ? null : format(v);
    }

    /** Validates and stores the four sash positions; returns false if any is out of range. */
    @Transactional
    public boolean save(Long adminId, double wT, double wB, double hL, double hR) {
        if (adminId == null) return false;
        double[] v = {wT, wB, hL, hR};
        for (double d : v) if (!(d >= MIN_PCT && d <= MAX_PCT)) return false;
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
        if (parts.length != 4) return null;
        double[] v = new double[4];
        try {
            for (int i = 0; i < 4; i++) v[i] = Double.parseDouble(parts[i].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        for (double d : v) if (!(d >= MIN_PCT && d <= MAX_PCT)) return null;
        return v;
    }

    private static String format(double[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Math.round(v[i] * 10) / 10.0);
        }
        return sb.toString();
    }
}
