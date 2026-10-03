package com.crm.service;

import com.crm.entity.CrmSetting;
import com.crm.entity.ThreadMemo;
import com.crm.repository.CrmSettingRepository;
import com.crm.repository.ThreadMemoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 受信ボックス thread page data that is not part of a message (client request 2026-10-02):
 * the やり取りメモ of the user / キャラ cards and the ★ marks of the inbox list.
 */
@Service
public class ThreadPanelService {

    public static final int MEMO_MAX = 500;
    private static final String STAR_KEY = "inbox.starredUserIds";

    private final ThreadMemoRepository memoRepository;
    private final CrmSettingRepository settingRepository;

    public ThreadPanelService(ThreadMemoRepository memoRepository, CrmSettingRepository settingRepository) {
        this.memoRepository = memoRepository;
        this.settingRepository = settingRepository;
    }

    public static boolean isTarget(String target) {
        return ThreadMemo.TARGET_MEMBER.equals(target) || ThreadMemo.TARGET_STAFF.equals(target);
    }

    public String getMemo(Long userId, String target, long staffId) {
        if (userId == null || !isTarget(target)) return "";
        return memoRepository.findByUserIdAndTargetAndStaffId(userId, target, staffId)
                .map(ThreadMemo::getMemo).map(m -> m == null ? "" : m).orElse("");
    }

    /** Saves the memo; blank text deletes it. Returns false for an invalid target. */
    @Transactional
    public boolean saveMemo(Long userId, String target, long staffId, String memo) {
        if (userId == null || !isTarget(target)) return false;
        String text = memo == null ? "" : memo.trim();
        if (text.length() > MEMO_MAX) text = text.substring(0, MEMO_MAX);
        ThreadMemo m = memoRepository.findByUserIdAndTargetAndStaffId(userId, target, staffId).orElse(null);
        if (text.isEmpty()) {
            if (m != null) memoRepository.delete(m);
            return true;
        }
        if (m == null) {
            m = new ThreadMemo();
            m.setUserId(userId);
            m.setTarget(target);
            m.setStaffId(staffId);
        }
        m.setMemo(text);
        memoRepository.save(m);
        return true;
    }

    public Set<Long> starredUserIds() {
        Set<Long> out = new LinkedHashSet<>();
        String raw = settingRepository.findBySettingKey(STAR_KEY).map(CrmSetting::getSettingValue).orElse(null);
        if (raw == null) return out;
        for (String s : raw.split(",")) {
            try { out.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignored) { /* skip */ }
        }
        return out;
    }

    @Transactional
    public void setStarred(Long userId, boolean starred) {
        if (userId == null) return;
        Set<Long> ids = starredUserIds();
        boolean changed = starred ? ids.add(userId) : ids.remove(userId);
        if (!changed) return;
        CrmSetting s = settingRepository.findBySettingKey(STAR_KEY).orElseGet(() -> {
            CrmSetting n = new CrmSetting();
            n.setSettingKey(STAR_KEY);
            n.setDescription("受信ボックスで★を付けたユーザーID");
            return n;
        });
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        s.setSettingValue(sb.toString());
        settingRepository.save(s);
    }
}
