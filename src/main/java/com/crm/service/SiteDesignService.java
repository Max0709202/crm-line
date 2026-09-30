package com.crm.service;

import com.crm.entity.CrmSetting;
import com.crm.repository.CrmSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 番組デザイン設定 — operator-edited parts of the public member (番組) site:
 * <ul>
 *   <li>the pages linked from the site footer (FAQ, 年齢認証, 特定商取引法, プライバシーポリシー,
 *       利用規約, 料金表), each an HTML body stored as {@code site.page.<code>};</li>
 *   <li>replaceable images: the site logo ({@code %sitelogo%}, shown in the site headers) and
 *       the pre-login top image for PC / スマホ. Blank = the bundled default design;</li>
 *   <li>the お問い合わせ address the footer's mail link opens;</li>
 *   <li>the pre-login top page HTML (blank = the bundled client design) and the note shown in
 *       the fixed site footer (e.g. インターネット異性紹介事業の届出・受理番号);</li>
 *   <li>the post-login (会員) pages' free HTML areas. Those pages' layout is fixed; each page has
 *       an operator HTML area at the top (directly under the page-name bar, e.g. 受信BOX) and one
 *       at the bottom (just above the footer menu), plus page CSS output in the page's head.
 *       A blank area is not shown. Each HTML area is shown either to every member (全表示) or
 *       only to members in chosen folders (フォルダ限定) — see {@link #slotHtmlFor}.</li>
 * </ul>
 * All values live in CRM_SETTING.
 */
@Service
public class SiteDesignService {

    private static final String PAGE_PREFIX = "site.page.";
    public static final String KEY_TOP_IMAGE_PC = "site.top_image_pc_url";
    public static final String KEY_TOP_IMAGE_SP = "site.top_image_sp_url";
    public static final String KEY_CONTACT_EMAIL = "site.contact_email";
    public static final String KEY_FOOTER_NOTE = "site.footer_note";
    /** Stored in chunks (see {@link #saveLongText}) — CRM_SETTING.SETTING_VALUE is a MySQL TEXT
     *  (64KB) and the default design alone is ~52KB. */
    private static final String KEY_TOP_HTML = "site.top_html";
    private static final int CHUNK_CHARS = 15000; // ≤ 60,000 bytes even at 4 bytes/char
    public static final int MAX_TOP_HTML_CHARS = 500000;
    public static final String DEFAULT_FOOTER_NOTE = "18歳未満の方および高校生の方のご利用はお断りしています。";

    /** Footer pages editable on 番組デザイン設定, in footer order: code → title. */
    public static final Map<String, String> PAGES;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("faq",       "よくある質問");
        m.put("age",       "年齢認証");
        m.put("tokushoho", "特定商取引法に基づく表記");
        m.put("privacy",   "プライバシーポリシー");
        m.put("terms",     "利用規約");
        m.put("price",     "料金表");
        PAGES = Collections.unmodifiableMap(m);
    }

    /** Post-login pages, in menu order: code → title. Each has one free HTML area. */
    public static final Map<String, String> MEMBER_PAGES;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("menu",        "MENU");
        m.put("inbox",       "受信BOX");
        m.put("reply",       "返信・送信画面");
        m.put("friends",     "友達追加リスト");
        m.put("search",      "条件検索");
        m.put("support",     "サポート窓口");
        m.put("profile",     "プロフ編集");
        m.put("points",      "ポイント購入");
        m.put("point_table", "ポイント表");
        MEMBER_PAGES = Collections.unmodifiableMap(m);
    }

    /** Length limits of the post-login HTML areas (characters, line breaks count as one). */
    public static final int MAX_SLOT_HTML_CHARS = 8000;
    public static final int MAX_SLOT_CSS_CHARS = 8000;

    /** One post-login page's HTML areas and CSS; empty string when not set. Each area's
     *  folder list is empty for 全表示 (every member), else the only folders it's shown to. */
    public static final class Slot {
        private final String code;
        private final String title;
        private final String topHtml;
        private final String bottomHtml;
        private final String css;
        private final java.util.List<String> topFolders;
        private final java.util.List<String> bottomFolders;

        Slot(String code, String title, String topHtml, String bottomHtml, String css,
             java.util.List<String> topFolders, java.util.List<String> bottomFolders) {
            this.code = code;
            this.title = title;
            this.topHtml = topHtml;
            this.bottomHtml = bottomHtml;
            this.css = css;
            this.topFolders = topFolders;
            this.bottomFolders = bottomFolders;
        }

        public String getCode() { return code; }
        public String getTitle() { return title; }
        public String getTopHtml() { return topHtml; }
        public String getBottomHtml() { return bottomHtml; }
        public String getCss() { return css; }
        public java.util.List<String> getTopFolders() { return topFolders; }
        public java.util.List<String> getBottomFolders() { return bottomFolders; }
    }

    /** HTML area positions on a post-login page. */
    public static final String POSITION_TOP = "top";
    public static final String POSITION_BOTTOM = "bottom";

    private static final String SLOT_PREFIX = "site.slot.";

    private final CrmSettingRepository repository;
    private final DomainSettingService domainSettingService;

    public SiteDesignService(CrmSettingRepository repository, DomainSettingService domainSettingService) {
        this.repository = repository;
        this.domainSettingService = domainSettingService;
    }

    /** Current HTML of each footer page (empty string when not yet written), keyed by code. */
    public Map<String, String> getPages() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String code : PAGES.keySet()) out.put(code, getPageHtml(code));
        return out;
    }

    public String getPageHtml(String code) {
        String v = get(PAGE_PREFIX + code);
        return v == null ? "" : v;
    }

    /** Saves the submitted footer pages; codes not in {@link #PAGES} are ignored. */
    @Transactional
    public void savePages(Map<String, String> htmlByCode) {
        for (String code : PAGES.keySet()) {
            String html = htmlByCode.get(code);
            if (html != null) save(PAGE_PREFIX + code, html);
        }
    }

    /** Site name for %sitename% — set on ドメイン設定; falls back to the main domain's host. */
    public String getSiteName() {
        String name = domainSettingService.getSiteName();
        if (name != null && !name.trim().isEmpty()) return name.trim();
        String host = mainDomainHost();
        return host == null ? "" : host;
    }

    public String getLogoUrl()       { return blankToNull(get(DomainSettingService.KEY_SITE_LOGO_URL)); }
    public String getTopImagePcUrl() { return blankToNull(get(KEY_TOP_IMAGE_PC)); }
    public String getTopImageSpUrl() { return blankToNull(get(KEY_TOP_IMAGE_SP)); }

    /** Stores (or with null/blank, clears) one of the image keys above. */
    @Transactional
    public void saveImageUrl(String key, String url) {
        save(key, url == null ? "" : url.trim());
    }

    /** The saved お問い合わせ address, or null when the operator hasn't set one. */
    public String getContactEmailSetting() { return blankToNull(get(KEY_CONTACT_EMAIL)); }

    /** Address the footer お問い合わせ link mails: the saved one, else info@<main domain>. */
    public String getContactEmail() {
        String v = getContactEmailSetting();
        if (v != null) return v;
        String host = mainDomainHost();
        return host == null ? null : "info@" + host;
    }

    @Transactional
    public void saveContactEmail(String email) {
        save(KEY_CONTACT_EMAIL, email == null ? "" : email.trim());
    }

    /** All post-login HTML areas in page order. */
    public java.util.List<Slot> getSlots() {
        java.util.List<Slot> out = new java.util.ArrayList<>();
        for (Map.Entry<String, String> p : MEMBER_PAGES.entrySet()) {
            String code = p.getKey();
            String key = SLOT_PREFIX + code;
            String top = get(key + ".top");
            String bottom = get(key + ".bottom");
            if (top == null && bottom == null) {
                // Saved before the top / bottom split: one area plus its position.
                String legacy = getLongText(key + ".html");
                String pos = get(key + ".position");
                if (legacy != null && "top".equals(pos)) top = legacy;
                if (legacy != null && "bottom".equals(pos)) bottom = legacy;
            }
            String css = get(key + ".css");
            out.add(new Slot(code, p.getValue(), nz(top), nz(bottom), nz(css),
                    getFolders(key + ".top.folders"), getFolders(key + ".bottom.folders")));
        }
        return out;
    }

    /**
     * Saves one page's top / bottom HTML and CSS; unknown pages are ignored, a null field is
     * left as it is. Nothing is saved when any field is over its limit.
     */
    @Transactional
    public void saveSlot(String code, String topHtml, String bottomHtml, String css) {
        if (!MEMBER_PAGES.containsKey(code)) return;
        String top = normalizeNewlines(topHtml);
        String bottom = normalizeNewlines(bottomHtml);
        String c = normalizeNewlines(css);
        java.util.List<String> over = new java.util.ArrayList<>();
        if (top != null && top.length() > MAX_SLOT_HTML_CHARS) over.add("上部HTMLは" + MAX_SLOT_HTML_CHARS + "文字まで（" + top.length() + "文字）");
        if (bottom != null && bottom.length() > MAX_SLOT_HTML_CHARS) over.add("下部HTMLは" + MAX_SLOT_HTML_CHARS + "文字まで（" + bottom.length() + "文字）");
        if (c != null && c.length() > MAX_SLOT_CSS_CHARS) over.add("CSSは" + MAX_SLOT_CSS_CHARS + "文字まで（" + c.length() + "文字）");
        if (!over.isEmpty()) throw new IllegalArgumentException(String.join("、", over));
        String key = SLOT_PREFIX + code;
        if (top != null) save(key + ".top", top);
        if (bottom != null) save(key + ".bottom", bottom);
        if (c != null) save(key + ".css", c);
    }

    /**
     * Saves which folders each of a page's HTML areas is shown to; an empty list = 全表示.
     * Unknown pages are ignored, a null list leaves that area's setting as it is.
     */
    @Transactional
    public void saveSlotFolders(String code, java.util.List<String> topFolders, java.util.List<String> bottomFolders) {
        if (!MEMBER_PAGES.containsKey(code)) return;
        String key = SLOT_PREFIX + code;
        if (topFolders != null) save(key + ".top.folders", joinFolders(topFolders));
        if (bottomFolders != null) save(key + ".bottom.folders", joinFolders(bottomFolders));
    }

    /**
     * The HTML a member in {@code memberFolder} sees in one area of a post-login page: the
     * area's HTML when it's 全表示 or limited to a folder list containing theirs, otherwise
     * "" (nothing shown). For the member pages to call when rendering each area.
     */
    public String slotHtmlFor(String code, String position, String memberFolder) {
        for (Slot slot : getSlots()) {
            if (!slot.getCode().equals(code)) continue;
            boolean top = POSITION_TOP.equals(position);
            if (!top && !POSITION_BOTTOM.equals(position)) return "";
            java.util.List<String> folders = top ? slot.getTopFolders() : slot.getBottomFolders();
            if (!folders.isEmpty() && (memberFolder == null || !folders.contains(memberFolder))) return "";
            return top ? slot.getTopHtml() : slot.getBottomHtml();
        }
        return "";
    }

    /** Folder names are free text (may contain commas), so one per line. */
    private static String joinFolders(java.util.List<String> folders) {
        java.util.LinkedHashSet<String> uniq = new java.util.LinkedHashSet<>();
        for (String f : folders) {
            if (f != null && !f.trim().isEmpty()) uniq.add(f.trim());
        }
        return String.join("\n", uniq);
    }

    private java.util.List<String> getFolders(String key) {
        String v = get(key);
        java.util.List<String> out = new java.util.ArrayList<>();
        if (v == null) return out;
        for (String f : v.split("\n")) {
            if (!f.trim().isEmpty()) out.add(f.trim());
        }
        return out;
    }

    private static String normalizeNewlines(String v) {
        return v == null ? null : v.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }

    /** Operator-edited pre-login page HTML, or null when the default design is in use. */
    public String getTopHtml() {
        String v = getLongText(KEY_TOP_HTML);
        return (v == null || v.trim().isEmpty()) ? null : v;
    }

    /** Saves the pre-login page HTML; null/blank goes back to the default design. */
    @Transactional
    public void saveTopHtml(String html) {
        if (html != null && html.length() > MAX_TOP_HTML_CHARS) {
            throw new IllegalArgumentException("HTMLが長すぎます（" + MAX_TOP_HTML_CHARS + "文字まで）");
        }
        saveLongText(KEY_TOP_HTML, html == null ? "" : html);
    }

    /** Note printed in the fixed footer (plain text, line breaks kept). */
    public String getFooterNote() {
        String v = get(KEY_FOOTER_NOTE);
        return v == null ? DEFAULT_FOOTER_NOTE : v;
    }

    @Transactional
    public void saveFooterNote(String note) {
        save(KEY_FOOTER_NOTE, note == null ? "" : note.replace("\r\n", "\n").trim());
    }

    /** Host of the main (reply base) domain, e.g. "avu74g.jp"; null when not configured. */
    private String mainDomainHost() {
        String base = domainSettingService.getReplyBaseUrl();
        if (base == null || base.trim().isEmpty()) return null;
        try {
            String host = URI.create(base.trim()).getHost();
            return (host == null || host.isEmpty()) ? null : host;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Reads a value stored by {@link #saveLongText}: {@code <key>.count} chunks {@code <key>.<i>}. */
    private String getLongText(String key) {
        String count = get(key + ".count");
        if (count == null) return null;
        int n;
        try { n = Integer.parseInt(count.trim()); } catch (NumberFormatException e) { return null; }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) {
            String part = get(key + "." + i);
            if (part != null) b.append(part);
        }
        return b.toString();
    }

    /** Splits {@code value} over as many rows as needed; rows left over from a longer previous
     *  value are emptied so they can't be read back. */
    private void saveLongText(String key, String value) {
        int oldCount = 0;
        String prev = get(key + ".count");
        if (prev != null) {
            try { oldCount = Integer.parseInt(prev.trim()); } catch (NumberFormatException ignored) { }
        }
        int n = 0;
        int start = 0;
        while (start < value.length()) {
            int end = Math.min(value.length(), start + CHUNK_CHARS);
            // don't split a surrogate pair (emoji etc.) across two rows
            if (end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))) end--;
            save(key + "." + n, value.substring(start, end));
            n++;
            start = end;
        }
        for (int i = n; i < oldCount; i++) save(key + "." + i, "");
        save(key + ".count", String.valueOf(n));
    }

    private static String blankToNull(String v) {
        return (v == null || v.trim().isEmpty()) ? null : v.trim();
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
