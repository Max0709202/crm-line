package com.crm.service;

import com.crm.entity.CrmSetting;
import com.crm.repository.CrmSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages the configurable list of folder names users can be grouped into.
 * Stored in CRM_SETTING under key "folder.names" as a comma-separated string.
 */
@Service
public class FolderSettingService {

    public static final String KEY = "folder.names";
    /** Suggested archive folder name — seeded on first install (empty CRM_SETTING) but the
     *  operator can rename or delete it freely afterwards. No code depends on its presence. */
    public static final String ARCHIVE_FOLDER = "退避";
    public static final List<String> DEFAULT_FOLDERS =
            Collections.unmodifiableList(java.util.Arrays.asList("A", "B", "C", "D", ARCHIVE_FOLDER));

    /** フォルダ名の上限 (全角10文字まで, 2026-10-01 client request). */
    public static final int NAME_MAX = 10;
    /** Per-folder display color (#rrggbb) lives in CRM_SETTING under {@code folder.color.<name>}. */
    public static final String COLOR_KEY_PREFIX = "folder.color.";
    private static final java.util.regex.Pattern HEX_COLOR = java.util.regex.Pattern.compile("^#[0-9a-fA-F]{6}$");

    private final CrmSettingRepository repo;

    public FolderSettingService(CrmSettingRepository repo) { this.repo = repo; }

    public List<String> listFolders() {
        String raw = repo.findBySettingKey(KEY).map(CrmSetting::getSettingValue).orElse(null);
        if (raw == null || raw.trim().isEmpty()) return DEFAULT_FOLDERS;
        // LinkedHashSet preserves first-seen order while collapsing duplicates — defensive
        // against a malformed saved value (2026-05-21: operator's list had "ーーーーーーーー"
        // present twice, which surfaced as a doubled checkbox in the filter dropdown).
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) seen.add(t);
        }
        return new ArrayList<>(seen);
    }

    /** Replace the whole folder list with whatever the operator saved. No auto-injection
     *  of any "system" folder — the operator has full control over the list. */
    @Transactional
    public void save(List<String> folders) {
        java.util.LinkedHashSet<String> dedup = new java.util.LinkedHashSet<>();
        if (folders != null) {
            for (String s : folders) {
                if (s == null) continue;
                String t = s.trim();
                if (!t.isEmpty()) dedup.add(t);
            }
        }
        String value = String.join(",", dedup);
        CrmSetting s = repo.findBySettingKey(KEY).orElseGet(() -> {
            CrmSetting ns = new CrmSetting();
            ns.setSettingKey(KEY);
            ns.setDescription("Comma-separated folder names shown on the user list");
            ns.setUpdatedAt(LocalDateTime.now());
            return ns;
        });
        s.setSettingValue(value);
        s.setUpdatedAt(LocalDateTime.now());
        repo.save(s);
    }

    /** Names in {@code folders} longer than {@link #NAME_MAX} that aren't already saved —
     *  existing longer names are left alone so saving doesn't force a rename (which would
     *  strand the users on the old name). */
    public List<String> tooLongNewNames(List<String> folders) {
        java.util.Set<String> existing = new java.util.HashSet<>(listFolders());
        List<String> out = new ArrayList<>();
        if (folders == null) return out;
        for (String f : folders) {
            if (f == null) continue;
            String t = f.trim();
            if (t.codePointCount(0, t.length()) > NAME_MAX && !existing.contains(t)) out.add(t);
        }
        return out;
    }

    /** Folder name → #rrggbb for every configured folder that has a color set. */
    public java.util.Map<String, String> colorMap() {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        for (String name : listFolders()) {
            repo.findBySettingKey(COLOR_KEY_PREFIX + name)
                    .map(CrmSetting::getSettingValue)
                    .filter(v -> v != null && HEX_COLOR.matcher(v.trim()).matches())
                    .ifPresent(v -> out.put(name, v.trim()));
        }
        return out;
    }

    /** Saves each folder's color; a blank / malformed color clears it. {@code names} and
     *  {@code colors} are parallel lists from the フォルダ設定 form rows. */
    @Transactional
    public void saveColors(List<String> names, List<String> colors) {
        if (names == null) return;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i) == null ? "" : names.get(i).trim();
            if (name.isEmpty()) continue;
            String c = colors != null && i < colors.size() && colors.get(i) != null ? colors.get(i).trim() : "";
            String key = COLOR_KEY_PREFIX + name;
            java.util.Optional<CrmSetting> cur = repo.findBySettingKey(key);
            if (!HEX_COLOR.matcher(c).matches()) {
                cur.ifPresent(repo::delete);
                continue;
            }
            CrmSetting s = cur.orElseGet(() -> {
                CrmSetting ns = new CrmSetting();
                ns.setSettingKey(key);
                ns.setDescription("Display color of folder " + name);
                return ns;
            });
            s.setSettingValue(c.toLowerCase(java.util.Locale.ROOT));
            s.setUpdatedAt(LocalDateTime.now());
            repo.save(s);
        }
    }
}
