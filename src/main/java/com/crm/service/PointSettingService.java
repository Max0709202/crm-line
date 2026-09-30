package com.crm.service;

import com.crm.entity.CrmSetting;
import com.crm.repository.CrmSettingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ポイント設定 — how many points each member action consumes on the member (番組) site.
 * What is saved here is what members see on their ポイント表 page.
 *
 * Stored in CRM_SETTING as {@code point.cost.<code>} (integer points). The two attachment
 * items (アドレス添付 / 番号添付) additionally have a {@code point.show.<code>} flag: when off,
 * the option is hidden from the member reply form and from the ポイント表. Both default to on.
 * Defaults are the values from the client's ポイント表 design (2026-09-27).
 *
 * Also holds 初期所持ポイント ({@code point.initial}) — the balance a member starts with on
 * registration. Defaults to 0.
 *
 * フォルダごとの設定: the keys above are the 共通 (shared) values. A folder can have its own
 * costs / show flags, stored as one JSON row {@code point.folder.<folder name>}
 * ({"costs":{code:n},"shown":{code:bool}}). A folder without that row — or with it blank —
 * uses the 共通 values; an item missing from the JSON also falls back to 共通.
 * 初期所持ポイント is 共通 only.
 */
@Service
public class PointSettingService {

    public static final String CODE_ADDRESS_ATTACH = "address_attach";
    public static final String CODE_TEL_ATTACH     = "tel_attach";

    private static final String COST_PREFIX = "point.cost.";
    private static final String SHOW_PREFIX = "point.show.";
    private static final String KEY_INITIAL = "point.initial";
    private static final String FOLDER_PREFIX = "point.folder.";
    /** CRM_SETTING.SETTING_KEY is VARCHAR(128). */
    public static final int MAX_FOLDER_NAME = 128 - FOLDER_PREFIX.length();
    private static final ObjectMapper JSON = new ObjectMapper();
    public static final int MAX_COST = 100000;

    /** One consumable action. {@code toggleable} items can be hidden from members. */
    public static final class Item {
        private final String code;
        private final String label;
        private final int defaultCost;
        private final boolean toggleable;

        Item(String code, String label, int defaultCost, boolean toggleable) {
            this.code = code;
            this.label = label;
            this.defaultCost = defaultCost;
            this.toggleable = toggleable;
        }

        public String getCode() { return code; }
        public String getLabel() { return label; }
        public int getDefaultCost() { return defaultCost; }
        public boolean isToggleable() { return toggleable; }
    }

    /** Current value of one item, for the admin form and the member ポイント表. */
    public static final class Row {
        private final Item item;
        private final int cost;
        private final boolean shown;

        Row(Item item, int cost, boolean shown) {
            this.item = item;
            this.cost = cost;
            this.shown = shown;
        }

        public String getCode() { return item.getCode(); }
        public String getLabel() { return item.getLabel(); }
        public boolean isToggleable() { return item.isToggleable(); }
        public int getCost() { return cost; }
        public boolean isShown() { return shown; }
    }

    /** Display order = the order on the client's ポイント表. */
    public static final List<Item> ITEMS = Collections.unmodifiableList(Arrays.asList(
            new Item("mail_send",          "メール送信",             55,  false),
            new Item(CODE_TEL_ATTACH,      "メール送信電話番号添付", 600, true),
            new Item(CODE_ADDRESS_ATTACH,  "メール送信アドレス添付", 600, true),
            new Item("photo_attach",       "メール送信写真添付",     0,   false),
            new Item("body_view",          "本文閲覧",               0,   false),
            new Item("profile_view",       "プロフィール閲覧",       10,  false),
            new Item("photo_view",         "写真閲覧",               20,  false),
            new Item("profile_edit",       "プロフィール変更",       0,   false),
            new Item("profile_photo_edit", "プロフィール写真変更",   0,   false),
            new Item("search",             "検索",                   0,   false)
    ));

    private final CrmSettingRepository repository;

    public PointSettingService(CrmSettingRepository repository) {
        this.repository = repository;
    }

    /** All items with their current cost / visibility, in display order. */
    public List<Row> listAll() {
        List<Row> rows = new ArrayList<>();
        for (Item item : ITEMS) {
            rows.add(new Row(item, getCost(item), isShown(item)));
        }
        return rows;
    }

    /** Rows members see on the ポイント表 — hidden attachment items are left out. */
    public List<Row> listShown() {
        List<Row> rows = new ArrayList<>();
        for (Row r : listAll()) {
            if (r.isShown()) rows.add(r);
        }
        return rows;
    }

    /** Items as they apply to members in {@code folder} (null/blank = 共通). */
    public List<Row> listAll(String folder) {
        FolderValues fv = folderValues(folder);
        if (fv == null) return listAll();
        List<Row> rows = new ArrayList<>();
        for (Item item : ITEMS) {
            Integer cost = fv.costs.get(item.getCode());
            Boolean shown = item.isToggleable() ? fv.shown.get(item.getCode()) : Boolean.TRUE;
            rows.add(new Row(item, cost != null ? cost : getCost(item), shown != null ? shown : isShown(item)));
        }
        return rows;
    }

    public List<Row> listShown(String folder) {
        List<Row> rows = new ArrayList<>();
        for (Row r : listAll(folder)) {
            if (r.isShown()) rows.add(r);
        }
        return rows;
    }

    public int getCost(String code, String folder) {
        Item item = find(code);
        for (Row r : listAll(folder)) {
            if (r.item == item) return r.getCost();
        }
        return getCost(item);
    }

    public boolean isShown(String code, String folder) {
        Item item = find(code);
        for (Row r : listAll(folder)) {
            if (r.item == item) return r.isShown();
        }
        return isShown(item);
    }

    /** True when {@code folder} has its own settings instead of the 共通 values. */
    public boolean hasFolderSettings(String folder) {
        return folderValues(folder) != null;
    }

    /**
     * Saves one folder's settings. With {@code own} false the folder goes back to the 共通
     * values. Otherwise every item is stored for the folder; blank / invalid costs keep the value
     * the folder had so far (its own or 共通). Returns the labels whose value was rejected.
     */
    @Transactional
    public List<String> saveFolder(String folder, boolean own, Map<String, String> costs, List<String> shownCodes) {
        String key = folderKey(folder);
        if (!own) {
            if (get(key) != null) save(key, "");
            return new ArrayList<>();
        }
        List<String> rejected = new ArrayList<>();
        Map<String, Object> costOut = new LinkedHashMap<>();
        Map<String, Object> shownOut = new LinkedHashMap<>();
        for (Row current : listAll(folder)) {
            Item item = current.item;
            String raw = costs.get(item.getCode());
            Integer parsed = parseCost(raw);
            if (parsed == null && raw != null && !raw.trim().isEmpty()) rejected.add(item.getLabel());
            costOut.put(item.getCode(), parsed != null ? parsed : current.getCost());
            if (item.isToggleable()) {
                shownOut.put(item.getCode(), shownCodes != null && shownCodes.contains(item.getCode()));
            }
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("costs", costOut);
        root.put("shown", shownOut);
        try {
            save(key, JSON.writeValueAsString(root));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return rejected;
    }

    private static String folderKey(String folder) {
        if (folder == null || folder.trim().isEmpty()) throw new IllegalArgumentException("folder is required");
        String f = folder.trim();
        if (f.length() > MAX_FOLDER_NAME) {
            throw new IllegalArgumentException("フォルダ名が長すぎます（" + MAX_FOLDER_NAME + "文字まで）");
        }
        return FOLDER_PREFIX + f;
    }

    /** A folder's own values, or null when it uses 共通 (or folder is null/blank). */
    private FolderValues folderValues(String folder) {
        if (folder == null || folder.trim().isEmpty() || folder.trim().length() > MAX_FOLDER_NAME) return null;
        String v = get(FOLDER_PREFIX + folder.trim());
        if (v == null || v.trim().isEmpty()) return null;
        FolderValues fv = new FolderValues();
        try {
            JsonNode root = JSON.readTree(v);
            JsonNode c = root.path("costs");
            JsonNode sh = root.path("shown");
            for (Item item : ITEMS) {
                JsonNode n = c.get(item.getCode());
                if (n != null && n.canConvertToInt() && n.asInt() >= 0 && n.asInt() <= MAX_COST) {
                    fv.costs.put(item.getCode(), n.asInt());
                }
                JsonNode b = sh.get(item.getCode());
                if (b != null && b.isBoolean()) fv.shown.put(item.getCode(), b.asBoolean());
            }
        } catch (IOException e) {
            return null;   // unreadable row → behave as 共通 rather than break the page
        }
        return fv;
    }

    private static final class FolderValues {
        final Map<String, Integer> costs = new HashMap<>();
        final Map<String, Boolean> shown = new HashMap<>();
    }

    public int getInitialPoints() {
        Integer v = parseCost(get(KEY_INITIAL));
        return v == null ? 0 : v;
    }

    /** Saves 初期所持ポイント; returns false (and keeps the current value) when invalid.
     *  Blank is ignored like the per-item costs. */
    @Transactional
    public boolean saveInitialPoints(String raw) {
        Integer parsed = parseCost(raw);
        if (parsed == null) return raw == null || raw.trim().isEmpty();
        save(KEY_INITIAL, String.valueOf(parsed));
        return true;
    }

    public int getCost(String code) {
        return getCost(find(code));
    }

    public boolean isShown(String code) {
        return isShown(find(code));
    }

    /**
     * Saves every item. {@code costs} maps code → submitted text; blank or invalid values keep
     * the item's current cost. {@code shownCodes} lists the toggleable items that were ticked.
     * Returns the labels whose submitted value was rejected, so the caller can report them.
     */
    @Transactional
    public List<String> saveAll(Map<String, String> costs, List<String> shownCodes) {
        List<String> rejected = new ArrayList<>();
        for (Item item : ITEMS) {
            String raw = costs.get(item.getCode());
            Integer parsed = parseCost(raw);
            if (parsed == null) {
                if (raw != null && !raw.trim().isEmpty()) rejected.add(item.getLabel());
            } else {
                save(COST_PREFIX + item.getCode(), String.valueOf(parsed));
            }
            if (item.isToggleable()) {
                boolean shown = shownCodes != null && shownCodes.contains(item.getCode());
                save(SHOW_PREFIX + item.getCode(), String.valueOf(shown));
            }
        }
        return rejected;
    }

    private int getCost(Item item) {
        Integer v = parseCost(get(COST_PREFIX + item.getCode()));
        return v == null ? item.getDefaultCost() : v;
    }

    private boolean isShown(Item item) {
        if (!item.isToggleable()) return true;
        String v = get(SHOW_PREFIX + item.getCode());
        return v == null || "true".equalsIgnoreCase(v.trim());
    }

    private static Integer parseCost(String raw) {
        if (raw == null) return null;
        String t = raw.trim().replace(",", "");
        if (t.isEmpty()) return null;
        try {
            int n = Integer.parseInt(t);
            return (n < 0 || n > MAX_COST) ? null : n;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Item find(String code) {
        for (Item item : ITEMS) {
            if (item.getCode().equals(code)) return item;
        }
        throw new IllegalArgumentException("unknown point item: " + code);
    }

    private String get(String key) {
        return repository.findBySettingKey(key).map(CrmSetting::getSettingValue).orElse(null);
    }

    private void save(String key, String value) {
        CrmSetting s = repository.findBySettingKey(key).orElseGet(() -> {
            CrmSetting ns = new CrmSetting();
            ns.setSettingKey(key);
            return ns;
        });
        s.setSettingValue(value);
        repository.save(s);
    }
}
