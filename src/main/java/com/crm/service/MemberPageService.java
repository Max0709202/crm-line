package com.crm.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Post-login (会員) pages in the client-approved design ({@code site/member/}), with the
 * 番組デザイン設定 上部HTML / 下部HTML / CSS of each page placed where that screen describes:
 * <ul>
 *   <li>上部HTML — just under the page-name bar (MENU・受信BOX etc.); ガラケー: under the ID・PT row
 *       that follows the page-name bar;</li>
 *   <li>下部HTML — MENU: the design's free area {@code %HTML%}; other pages: just above the
 *       footer menu (ガラケー: above the page footer {@code <div class="f">}, else at the end);</li>
 *   <li>CSS — a {@code <style>} at the end of the page's head, scoped to the HTML areas
 *       (as is any {@code <style>} inside the HTML) so it can't recolor the design itself.</li>
 * </ul>
 * ポイント購入 lists the 決済関連設定 (共通) methods and plans in their saved order.
 * Member accounts are not built yet, so pages are rendered for the admin preview only, with
 * sample member values. ガラケー has its own design ({@code fp/}) for the pages in
 * {@link #FP_PAGES} (all of them), each with MENU's logo / page-name bar / ID・PT row header;
 * a page missing from it would be shown in the standard (スマホ) design.
 */
@Service
public class MemberPageService {

    private static final String DIR = "site/member/";

    /** Page code → name shown in the page-name bar (the approved design's wording). */
    public static final Map<String, String> BAR_TITLES;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("menu",        "MENU");
        m.put("inbox",       "受信BOX");
        m.put("reply",       "返信");
        m.put("friends",     "友達追加リスト");
        m.put("search",      "条件検索");
        m.put("support",     "サポート窓口");
        m.put("profile",     "プロフ編集");
        m.put("points",      "ポイント購入");
        m.put("point_table", "ポイント表");
        BAR_TITLES = Collections.unmodifiableMap(m);
    }

    /** Pages with a separate ガラケー design. */
    public static final Set<String> FP_PAGES =
            Collections.unmodifiableSet(new HashSet<>(BAR_TITLES.keySet()));

    /** Design file name (links inside the design) → page code. */
    private static final Map<String, String> FILE_TO_CODE = new HashMap<>();
    static {
        for (String code : BAR_TITLES.keySet()) FILE_TO_CODE.put(code.replace('_', '-'), code);
        FILE_TO_CODE.put("index", "menu");
    }
    private static final Pattern LINK = Pattern.compile("href=\"(?:\\.\\./)?([a-z-]+)\\.html\"");
    private static final Pattern TAG = Pattern.compile("%(sitename|sitelogo|id|name|point|toname|email)%");

    /** お知らせ — the member's 専用HTML (/member/memo). */
    public static final String MEMO = "memo";

    /** LINE用 page template tags (besides the member tags above). */
    private static final String LINE_MAIN = "%main%";
    /** LINE用: お問い合わせ（ID送信）フォーム — the member enters an ID and it arrives at サポート窓口. */
    public static final String SUPPORT_FORM_TAG = "%support_form%";
    private static final String SUPPORT_MARK = "<!--member-support-form-->";

    /** LINE用 page's footer menu ({@code <nav class="bottom">}) — rebuilt from 番組デザイン設定's checked items. */
    private static final Pattern LINE_FOOTER_NAV = Pattern.compile("(?is)<nav\\b[^>]*\\bclass=\"[^\"]*\\bbottom\\b[^\"]*\"[^>]*>.*?</nav\\s*>");

    /** One LINE用 footer item per {@link SiteDesignService#LINE_FOOTER_ITEMS} code (icons as in line/layout.html). */
    private static final Map<String, String> LINE_FOOTER_LINKS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("inbox", "<a href=\"/member/inbox\"><span class=\"bi\"><svg viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path fill=\"#4aa6e8\""
                + " d=\"M3 3h18a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H10l-5 4.5V17H3a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z\" />"
                + "<circle fill=\"#fff\" cx=\"8\" cy=\"10\" r=\"1.5\" /><circle fill=\"#fff\" cx=\"12\" cy=\"10\" r=\"1.5\" />"
                + "<circle fill=\"#fff\" cx=\"16\" cy=\"10\" r=\"1.5\" /></svg>%unread_badge%</span>受信BOX</a>");
        m.put("sent", "<a href=\"/member/inbox?tab=sent\"><span class=\"bi\"><svg viewBox=\"0 0 24 24\" aria-hidden=\"true\">"
                + "<path fill=\"#9a6ae6\" d=\"M2 21 23 12 2 3v7l15 2-15 2z\" /></svg></span>送信済</a>");
        m.put("memo", "<a href=\"/member/memo\"><span class=\"bi\"><svg viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path fill=\"#f5c84c\""
                + " d=\"M12 2a6 6 0 0 0-6 6v4.5L4 16v1h16v-1l-2-3.5V8a6 6 0 0 0-6-6zm0 21a3 3 0 0 0 3-3H9a3 3 0 0 0 3 3z\" /></svg>%news_new%</span>お知らせ</a>");
        m.put("points", "<a href=\"/member/points\"><span class=\"bi\"><svg viewBox=\"0 0 24 24\" aria-hidden=\"true\">"
                + "<ellipse fill=\"#c9a43e\" cx=\"12\" cy=\"12\" rx=\"11\" ry=\"8\" /><text x=\"12\" y=\"16.2\" fill=\"#fff\" font-size=\"11\""
                + " font-weight=\"900\" text-anchor=\"middle\" font-family=\"Arial,sans-serif\">P</text></svg></span>手続き</a>");
        // サポート窓口: the MENU's (TOP) headphone icon
        m.put("support", "<a href=\"/member/support\"><span class=\"bi\" style=\"line-height:1.1\">🎧</span>サポート窓口</a>");
        LINE_FOOTER_LINKS = Collections.unmodifiableMap(m);
    }

    private static final String MENU_FREE_AREA = "<div class=\"htmlslot\">%HTML%</div>";
    /** Class on every HTML area's wrapper; the operator's CSS only applies inside it. */
    private static final String SCOPE_CLASS = "member-free";
    private static final Pattern STYLE_BLOCK = Pattern.compile("(?is)(<style\\b[^>]*>)(.*?)(</style\\s*>)");
    private static final String FP_MENU_FREE_AREA = "<div class=\"row\">%HTML%</div>";
    /** ガラケー page footer (MENUへ戻る etc.); 下部HTML goes just above it. */
    private static final String FP_FOOTER = "<div class=\"f\">";

    private final SiteDesignService siteDesignService;
    private final PaymentSettingService paymentSettingService;
    private final Map<String, String> resources = new ConcurrentHashMap<>();

    public MemberPageService(SiteDesignService siteDesignService, PaymentSettingService paymentSettingService) {
        this.siteDesignService = siteDesignService;
        this.paymentSettingService = paymentSettingService;
    }

    /**
     * One post-login page as the admin preview shows it.
     *
     * @param code        page code ({@link SiteDesignService#MEMBER_PAGES}); unknown → MENU
     * @param device      "pc" / "sp" / "fp" (ガラケー)
     * @param markAreas   outline the 上部 / 下部 HTML areas with a label, empty ones included
     * @param linkForCode href for a link to another member page, given its page code
     */
    public String renderPreview(String code, String device, boolean markAreas, Function<String, String> linkForCode) {
        return render(code, device, markAreas, linkForCode, null, null, null, null, -1);
    }

    /**
     * One post-login page for a real member (e.g. プロフ編集 after 本登録): the member's own values
     * in the tags ({@code id} = login ID, {@code name}, {@code point}, {@code email}), and each HTML
     * area only when it's 全表示 or limited to the member's folder.
     */
    public String renderForMember(String code, String device, Map<String, String> memberValues, String memberFolder,
                                  Function<String, String> linkForCode) {
        return noZoom(device, render(code, device, false, linkForCode, memberValues, memberFolder == null ? "" : memberFolder, null, null, -1));
    }

    /**
     * One logged-in member page (会員ページ): the design's frame (logo, ID・PT, page-name bar, footer
     * menu, 上部 / 下部HTML, CSS) around {@code mainHtml} — the page's live content, already escaped
     * (null = the design's own content, e.g. MENU). {@code title} replaces the page-name bar text
     * (null = the page's own); {@code unread} is the 受信BOX count shown on the badges (0 = none).
     */
    public String renderMember(String code, String device, Map<String, String> memberValues, String memberFolder,
                               String mainHtml, String title, int unread, Function<String, String> linkForCode) {
        return noZoom(device, render(code, device, false, linkForCode, memberValues, memberFolder == null ? "" : memberFolder,
                mainHtml, title, Math.max(0, unread)));
    }

    /**
     * One LINE用 page (スマホ限定, members of the LINE folders): the page HTML saved on 番組デザイン設定
     * (blank = the bundled default) with its tags filled — %sitename% %sitelogo% %id% %name% %point%
     * %email%, %title% (page name), %unread_badge% (受信BOX's unread count, none = empty),
     * %news_new% (お知らせ's NEW while the 専用HTML is unread, else empty) and %main% (the page's
     * content, put in last so a member's own text is never read as a tag).
     *
     * @param page {@link SiteDesignService#LINE_PAGES} code; unknown → 受信BOX
     */
    public String renderLine(String page, Map<String, String> memberValues, String mainHtml, String title,
                             int unread, boolean newsNew) {
        return renderLine(page, memberValues, mainHtml, title, unread, newsNew, null);
    }

    /** Same, with {@code supportFormHtml} in the page's %support_form% (null = the tag is left empty). */
    public String renderLine(String page, Map<String, String> memberValues, String mainHtml, String title,
                             int unread, boolean newsNew, String supportFormHtml) {
        if (!SiteDesignService.LINE_PAGES.containsKey(page)) page = "inbox";
        return renderLine(page, siteDesignService.getLineHtml(page), memberValues, mainHtml, title, unread, newsNew, supportFormHtml);
    }

    /** {@code html} = the LINE用 page's HTML (null/blank = the bundled default). */
    private String renderLine(String page, String html, Map<String, String> memberValues, String mainHtml, String title,
                              int unread, boolean newsNew, String supportFormHtml) {
        if (html == null || html.trim().isEmpty()) html = getDefaultLineTemplate();
        html = lineFooter(html);
        // %main% → a marker first, so a member's own value (e.g. a nickname "%main%") is never read as it
        html = html.replace(LINE_MAIN, MAIN_MARK).replace(SUPPORT_FORM_TAG, SUPPORT_MARK)
                .replace("%title%", esc(title == null ? SiteDesignService.LINE_PAGES.get(page) : title))
                // 受信BOX's count: own small fixed size (like NEW) — the .bi's 25px made it cover「受信BOX」
                .replace("%unread_badge%", unread > 0 ? "<i class=\"fb\" style=\"" + LINE_BADGE_STYLE + "\">" + unread + "</i>" : "")
                // NEW: own small size so it stays inside お知らせ (the .bi's 25px pushed it over 手続き)
                .replace("%news_new%", newsNew ? "<i class=\"fb\" style=\"font-size:10px;line-height:1.6;padding:1px 6px;margin-left:2px\">NEW</i>" : "");
        html = fillTags(html, memberValues).replace(SUPPORT_MARK, supportFormHtml == null ? "" : supportFormHtml)
                .replace(MAIN_MARK, mainHtml == null ? "" : mainHtml);
        return com.crm.util.NoZoom.apply(lineHeader(html));
    }

    /**
     * LINE用 footer: the page's {@code <nav class="bottom">} (saved HTML or the bundled default) becomes
     * the items checked on 番組デザイン設定 — 1 to 4 equal-width items, none = no footer. A page whose
     * HTML has no such nav is left as it is.
     */
    private String lineFooter(String html) {
        Matcher m = LINE_FOOTER_NAV.matcher(html);
        if (!m.find()) return html;
        java.util.List<String> items = siteDesignService.getLineFooterItems();
        String nav;
        if (items.isEmpty()) {
            nav = "<style>body{padding-bottom:0!important}</style>";
        } else {
            StringBuilder b = new StringBuilder("<nav class=\"bottom\" style=\"grid-template-columns:repeat(")
                    .append(items.size()).append(",1fr)\">");
            for (String code : items) b.append(LINE_FOOTER_LINKS.get(code));
            nav = b.append("</nav>").toString();
        }
        return html.substring(0, m.start()) + nav + html.substring(m.end());
    }

    /**
     * LINE用 サポート窓口 (/member/support): お問い合わせ内容 + 送信 only — no お名前 / メールアドレス (LINE
     * members often have no e-mail). {@code sample} (the admin check) never submits.
     */
    public static String lineSupportForm(String csrfInput, boolean sample) {
        return "<form method=\"post\" action=\"/member/support\"" + (sample ? " onsubmit=\"return false\"" : "") + ">"
                + (csrfInput == null ? "" : csrfInput)
                + "<div class=\"row\"><div class=\"key\">お問い合わせ内容</div>"
                + "<div class=\"field\"><textarea name=\"body\" maxlength=\"" + SupportDeskService.BODY_MAX + "\" required></textarea></div></div>"
                + "<div class=\"actions\"><button class=\"btn\">送信する</button></div>"
                + "</form>";
    }

    /**
     * %support_form%: ID + 送信, posted to /member/support/id (→ サポート窓口), back to {@code back}.
     * {@code csrfInput} is the page's hidden _csrf field; {@code sample} (the admin check) never submits.
     */
    public static String supportForm(String csrfInput, String back, boolean sample) {
        return supportForm(csrfInput, back, sample, null, null);
    }

    /**
     * Same, with the result of the last 送信 shown in the form itself (the page's own HTML may have no
     * %main%): {@code done} = 送信しました, {@code error} in #formError. #supportForm / #message /
     * #formError are the ids a page's own check script uses (e.g. 「16桁で入力してください」).
     */
    public static String supportForm(String csrfInput, String back, boolean sample, String done, String error) {
        return "<form id=\"supportForm\" method=\"post\" action=\"/member/support/id\" class=\"support-id-form\" style=\"margin:12px 0;scroll-margin-top:160px\""
                + (sample ? " onsubmit=\"return false\"" : "") + ">" + (csrfInput == null ? "" : csrfInput)
                + "<input type=\"hidden\" name=\"back\" value=\"" + esc(back) + "\">"
                + "<label style=\"display:block;font-size:13px;margin-bottom:4px\">ID</label>"
                + "<div style=\"display:flex;gap:6px\">"
                + "<input type=\"text\" id=\"message\" name=\"id\" maxlength=\"100\" required placeholder=\"IDを入力\""
                + " style=\"flex:1;min-width:0;padding:8px;border:1px solid #ccc;border-radius:6px;font-size:16px\">"
                + "<button type=\"submit\" style=\"padding:8px 16px;border:0;border-radius:6px;background:#06c755;color:#fff;font-size:15px\">送信</button>"
                + "</div>"
                + "<div id=\"formError\" role=\"alert\" style=\"min-height:1.5em;margin-top:8px;color:#dc2626;font-size:13px;font-weight:700\">"
                + (error == null ? "" : esc(error)) + "</div>"
                + (done == null ? "" : "<div class=\"support-id-done\" style=\"margin-top:4px;padding:10px;border-radius:6px;background:#f0fdf4;"
                        + "border:1px solid #bbf7d0;color:#15803d;font-size:14px;font-weight:700\">" + esc(done) + "</div>")
                + "</form>";
    }

    /**
     * LINE用 header: the サイトロゴ across the full screen width (not %sitelogo%'s fixed small size), and
     * ID・ユーザー名 under it at the right edge. As CSS added to the page's own (saved HTML included), so
     * every LINE用 page shows it whatever HTML is saved.
     */
    private static final String LINE_HEADER_CSS = "<style>"
            + ".top .head{flex-wrap:wrap}"
            + ".top .head>div:first-child{flex:0 0 100%;min-width:0}"
            + ".top .sitelogo{margin-top:0}"
            + ".top .sitelogo img{display:block;width:calc(100% + 40px)!important;max-width:none!important;max-height:none!important;"
            + "height:auto!important;margin:-14px -20px 6px}"
            + ".top .acct{margin-left:auto;text-align:right}"
            + "@media(max-width:700px){.top .sitelogo img{width:calc(100% + 20px)!important;margin:-10px -10px 6px}}"
            + "</style>";

    private static String lineHeader(String html) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)</head\\s*>").matcher(html);
        return m.find() ? html.substring(0, m.start()) + LINE_HEADER_CSS + html.substring(m.start()) : LINE_HEADER_CSS + html;
    }

    /** The LINE用 受信BOX badge's size (fixed, whatever the page's CSS gives the footer icon). */
    private static final String LINE_BADGE_STYLE = "font-size:10px;line-height:1.6;padding:1px 6px;min-width:18px;text-align:center";

    /** LINE用 check's pages: {@link SiteDesignService#LINE_PAGES} plus 送信済 (the 受信BOX page's design). */
    public static final Map<String, String> LINE_PREVIEW_PAGES;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("inbox", SiteDesignService.LINE_PAGES.get("inbox"));
        m.put("sent", "送信済");
        for (Map.Entry<String, String> e : SiteDesignService.LINE_PAGES.entrySet()) m.putIfAbsent(e.getKey(), e.getValue());
        m.put("support", "サポート窓口");
        LINE_PREVIEW_PAGES = Collections.unmodifiableMap(m);
    }

    /** 番組デザイン設定 › LINE用 check: one LINE用 page with the saved HTML and sample content. */
    public String renderLinePreview(String page) {
        if (!LINE_PREVIEW_PAGES.containsKey(page)) page = "inbox";
        return renderLinePreview(page, siteDesignService.getLineHtml(lineFramePage(page)));
    }

    /**
     * Same, with the HTML being edited (not yet saved) — 「この画面を確認」 next to each LINE用 HTML box.
     * LINE専用 sample: no 写真閲覧 / プロフ, the LINE folder's ポイント設定, and the footer menu opens the
     * other LINE用 checks (not the member pages — those asked the admin to log in).
     */
    public String renderLinePreview(String page, String html) {
        if (!LINE_PREVIEW_PAGES.containsKey(page)) page = "inbox";
        String main = "points".equals(page)
                ? load("points.html").replace("%plans%", pointPlans(firstLineFolder()))
                : "support".equals(page) ? "<section class=\"box\"><div class=\"boxline\"></div>" + lineSupportForm("", true)
                        + "<div class=\"boxline\"></div></section>"
                : load("line/sample-" + page + ".html");
        String title = "inbox".equals(page) ? "受信BOX" : "sent".equals(page) ? "送信済" : "reply".equals(page) ? "返信"
                : "memo".equals(page) ? "お知らせ" : "support".equals(page) ? "サポート窓口" : "ポイント購入";
        String out = renderLine(lineFramePage(page), html, null, fillTags(main, null), title, 17, true,
                supportForm("", "/member/points", true));
        return out.replace("href=\"/member/inbox?tab=sent\"", "href=\"" + LINE_PREVIEW_URL + "sent\"")
                .replace("href=\"/member/inbox\"", "href=\"" + LINE_PREVIEW_URL + "inbox\"")
                .replace("href=\"/member/memo\"", "href=\"" + LINE_PREVIEW_URL + "memo\"")
                .replace("href=\"/member/points\"", "href=\"" + LINE_PREVIEW_URL + "points\"")
                .replace("href=\"/member/support\"", "href=\"" + LINE_PREVIEW_URL + "support\"");
    }

    private static final String LINE_PREVIEW_URL = "/manager/settings/site-design/line-preview?page=";

    /** The LINE用 page whose HTML frames a check page: 送信済 and サポート窓口 use 受信BOX's (as the member pages do). */
    private static String lineFramePage(String page) {
        return "sent".equals(page) || "support".equals(page) ? "inbox" : page;
    }

    /** The first LINE folder (its ポイント設定 is what a LINE member sees); null = none → 共通. */
    private String firstLineFolder() {
        List<String> folders = siteDesignService.getLineFolders();
        return folders.isEmpty() ? null : folders.get(0);
    }

    /** The bundled LINE用 page (the same frame for every LINE用 page). */
    public String getDefaultLineTemplate() {
        return load("line/layout.html");
    }

    /** Member pages can't be zoomed ({@link com.crm.util.NoZoom}); the ガラケー design gets {@link #fpViewport} instead. */
    private static String noZoom(String device, String html) {
        return "fp".equals(device) ? fpViewport(html) : com.crm.util.NoZoom.apply(html);
    }

    /**
     * ガラケー design: the same viewport / text size on every page (MENU and every page after it).
     * The fp/ designs had no viewport, so a browser fell back to its own (wide) default width and
     * showed the pages after MENU shrunk; long words wrap instead of widening the page.
     */
    private static final String FP_HEAD = "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<style>html{-webkit-text-size-adjust:100%;text-size-adjust:100%}body{overflow-wrap:break-word;word-wrap:break-word}</style>";
    private static final Pattern FP_CHARSET = Pattern.compile("(?i)<meta\\s+charset=[\"']?utf-8[\"']?\\s*/?>");

    private static String fpViewport(String html) {
        if (html == null) return null;
        int headEnd = html.indexOf("</head>");
        if (headEnd >= 0 && html.substring(0, headEnd).contains("name=\"viewport\"")) return html;
        Matcher m = FP_CHARSET.matcher(html);
        if (m.find()) return html.substring(0, m.end()) + FP_HEAD + html.substring(m.end());
        int head = html.indexOf("<head>");
        return head < 0 ? html : html.substring(0, head + 6) + FP_HEAD + html.substring(head + 6);
    }

    /** Where a member page's live content goes (put in after the tags are filled, so a member's
     *  own text like "%point%" is never read as a tag). */
    private static final String MAIN_MARK = "<!--member-main-->";

    /** {@code memberFolder} null = admin preview (every area shown); else a member in that folder ("" = none). */
    private String render(String code, String device, boolean markAreas, Function<String, String> linkForCode,
                          Map<String, String> memberValues, String memberFolder,
                          String mainHtml, String title, int unread) {
        // お知らせ (専用HTML): its content is the member's 専用HTML — shown in the サポート窓口 page's
        // frame, without that page's 上部 / 下部HTML
        boolean areas = !MEMO.equals(code);
        if (MEMO.equals(code)) code = "support";
        if (!BAR_TITLES.containsKey(code)) code = "menu";
        boolean fp = "fp".equals(device) && FP_PAGES.contains(code);
        SiteDesignService.Slot slot = slotFor(code);
        String barTitle = title == null ? BAR_TITLES.get(code) : title;

        String html;
        if (fp) {
            html = load("fp/" + code + ".html");
            if (mainHtml != null) {
                // the page's content: after the ID / PT row, up to the page footer
                int acct = html.indexOf("<div class=\"acct\">");
                int start = acct < 0 ? -1 : html.indexOf("</div>", acct);
                int end = html.lastIndexOf(FP_FOOTER);
                if (end < 0) end = html.lastIndexOf("</body>");
                if (start >= 0 && end > start) html = html.substring(0, start + 6) + MAIN_MARK + html.substring(end);
                if (title != null) html = html.replace("<header>" + BAR_TITLES.get(code) + "</header>", "<header>" + esc(title) + "</header>")
                        .replace("<title>" + BAR_TITLES.get(code) + "</title>", "<title>" + esc(title) + "</title>");
            } else if ("points".equals(code)) {
                html = html.replace("%plans%", fpPointPlans());
            }
        } else {
            html = load("layout.html")
                    .replace("%title%", esc(barTitle))
                    .replace("%main%", mainHtml != null ? MAIN_MARK : "points".equals(code)
                            ? load("points.html").replace("%plans%", pointPlans())
                            : load(code + ".html"));
        }
        html = fillTags(html, memberValues);
        if (mainHtml != null) html = html.replace(MAIN_MARK, mainHtml);
        if (unread >= 0) html = memberChrome(html, unread);

        String topHtml = areas ? slot.getTopHtml() : "", bottomHtml = areas ? slot.getBottomHtml() : "";
        if (memberFolder != null && areas) {
            String folder = memberFolder.isEmpty() ? null : memberFolder;
            topHtml = siteDesignService.slotHtmlFor(code, SiteDesignService.POSITION_TOP, folder);
            bottomHtml = siteDesignService.slotHtmlFor(code, SiteDesignService.POSITION_BOTTOM, folder);
        }
        String top = area("上部HTML", topHtml, slot.getTopFolders(), markAreas);
        String bottom = area("下部HTML", bottomHtml, slot.getBottomFolders(), markAreas);
        if (fp) {
            // every page: under the ID / PT row that follows the page-name bar
            html = insertAfter(html, "</div>", html.indexOf("<div class=\"acct\">"), wrapFp(top));
            if ("menu".equals(code)) {
                html = html.replace(FP_MENU_FREE_AREA, bottom.isEmpty() ? "" : "<div class=\"row " + SCOPE_CLASS + "\">" + bottom + "</div>");
            } else {
                html = insertBefore(html, html.contains(FP_FOOTER) ? FP_FOOTER : "</body>", wrapFp(bottom));
            }
        } else {
            // every page: just under the page-name bar (MENU・受信BOX …)
            html = insertAfter(html, "</div>", html.indexOf("<div class=\"bar\">"), wrapTop(top));
            if ("menu".equals(code)) {
                html = html.replace(MENU_FREE_AREA, bottom.isEmpty() ? "" : "<div class=\"htmlslot " + SCOPE_CLASS + "\">" + bottom + "</div>");
            } else {
                html = insertBefore(html, "</main>", bottom.isEmpty() ? ""
                        : "<div class=\"member-area member-area-bottom " + SCOPE_CLASS + "\" style=\"margin-top:15px\">" + bottom + "</div>");
            }
        }

        String css = areas ? slot.getCss() : null;
        if (css != null && !css.trim().isEmpty()) {
            html = insertBefore(html, "</head>", "<style>\n" + scopeCss(css) + "\n</style>");
        }
        return rewriteLinks(html, linkForCode);
    }

    /**
     * The logged-in member's real values in the design's fixed parts: the 受信BOX badges (the
     * design's sample "17"), the MENU's 利用規約 etc. links (no お問い合わせ — サポート窓口 is in the MENU)
     * and a ログアウト link, and the ガラケー
     * footer's ポイント購入 link.
     */
    private static String memberChrome(String html, int unread) {
        String n = String.valueOf(unread);
        return html
                .replace("<span class=\"badge\">17</span>", unread > 0 ? "<span class=\"badge\">" + n + "</span>" : "")
                // footer 受信BOX count: the LINE用 badge's small fixed size, so it's a round badge rather than the
                // tall one the .bi's 25px gave it (client request 2026-10-14: LINEカスタマイズと同じように)
                .replace("<i class=\"fb\">17</i>", unread > 0 ? "<i class=\"fb\" style=\"" + LINE_BADGE_STYLE + "\">" + n + "</i>" : "")
                .replace("受信BOX(17)", unread > 0 ? "受信BOX(" + n + ")" : "受信BOX")
                .replace("<a href=\"#\">利用規約　›</a><a href=\"#\">特定商取引法　›</a><a href=\"#\">プライバシーポリシー　›</a>",
                        "<a href=\"/page/terms\">利用規約　›</a><a href=\"/page/tokushoho\">特定商取引法　›</a>"
                                + "<a href=\"/page/privacy\">プライバシーポリシー　›</a>"
                                + "<a href=\"/member/logout\">ログアウト　›</a>")
                .replace("<a href=\"#\">利用規約</a>｜<a href=\"#\">特定商取引</a><br><a href=\"#\">プライバシーポリシー</a>",
                        "<a href=\"/page/terms\">利用規約</a>｜<a href=\"/page/tokushoho\">特定商取引</a><br>"
                                + "<a href=\"/page/privacy\">プライバシーポリシー</a><br>"
                                + "<a href=\"/member/logout\">ログアウト</a>")
                .replace("<a href=\"#\">ポイント購入</a>", "<a href=\"/member/points\">ポイント購入</a>");
    }

    /** ポイント購入: each shown payment method (saved order and name) with its plans. */
    private String pointPlans() {
        return pointPlans(null);
    }

    /** The same for a member in {@code folder} (its own ポイント設定, else 共通). */
    private String pointPlans(String folder) {
        StringBuilder b = new StringBuilder();
        for (PaymentSettingService.Method m : paymentSettingService.getMethods(folder)) {
            List<PaymentSettingService.Plan> plans = m.getOfferedPlans();
            if (plans.isEmpty()) continue;
            b.append("<h3 class=\"methodtitle\">").append(esc(m.getLabel())).append("</h3>");
            for (PaymentSettingService.Plan p : plans) {
                b.append("<div class=\"person\"><div class=\"grow\"><b>")
                        .append(String.format("%,d", p.getPoints())).append("ポイント</b><span>")
                        .append(String.format("%,d", p.getAmount())).append("円(税込)</span></div>")
                        .append("<button class=\"btn\">購入する</button></div>");
            }
        }
        return b.length() == 0 ? "<div class=\"note\">現在購入できるプランはありません。</div>" : b.toString();
    }

    /** ガラケー ポイント購入: the same methods / plans in the ガラケー design's list style. */
    private String fpPointPlans() {
        StringBuilder b = new StringBuilder();
        for (PaymentSettingService.Method m : paymentSettingService.getMethods(null)) {
            List<PaymentSettingService.Plan> plans = m.getOfferedPlans();
            if (plans.isEmpty()) continue;
            b.append("<div class=\"ttl\">").append(esc(m.getLabel())).append("</div><div class=\"m\">");
            for (PaymentSettingService.Plan p : plans) {
                b.append("<a href=\"#\">").append(String.format("%,d", p.getPoints())).append("ポイント　¥")
                        .append(String.format("%,d", p.getAmount())).append("</a>");
            }
            b.append("</div>");
        }
        return b.length() == 0 ? "<div class=\"row\">現在購入できるプランはありません。</div>" : b.toString();
    }

    private SiteDesignService.Slot slotFor(String code) {
        for (SiteDesignService.Slot s : siteDesignService.getSlots()) {
            if (s.getCode().equals(code)) return s;
        }
        throw new IllegalStateException("no slot for member page " + code);
    }

    /** The design's member tags: the member's values when given, else sample values (admin preview). */
    private String fillTags(String html, Map<String, String> memberValues) {
        String logo = siteDesignService.getLogoUrl();
        Map<String, String> values = new HashMap<>();
        // never the bare domain; with a サイトロゴ registered the logo replaces the name (no double title)
        values.put("sitename", logo == null ? esc(siteDesignService.getConfiguredSiteName()) : "");
        values.put("sitelogo", logo == null ? ""
                : "<img src=\"" + esc(logo) + "\" alt=\"\" style=\"max-height:40px;max-width:100%\">");
        values.put("id", "000123");
        values.put("name", "サンプル");
        values.put("point", "1,000");
        values.put("toname", "まい");
        values.put("email", "sample@example.jp");
        if (memberValues != null) {
            for (Map.Entry<String, String> e : memberValues.entrySet()) values.put(e.getKey(), esc(e.getValue()));
        }
        Matcher m = TAG.matcher(html);
        StringBuffer out = new StringBuffer();
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(values.get(m.group(1))));
        m.appendTail(out);
        return out.toString();
    }

    /**
     * An HTML area's content: the saved HTML, or "" when blank (a blank area isn't shown). With
     * {@code mark}, the area is outlined and labelled — blank ones too, so every position shows.
     * Folder-limited areas are shown in the preview, with the folders in the label.
     */
    private static String area(String label, String html, List<String> folders, boolean mark) {
        boolean blank = html == null || html.trim().isEmpty();
        if (!blank) html = scopeStyleBlocks(html);
        if (!mark) return blank ? "" : html;
        String scope = folders.isEmpty() ? "全表示" : "フォルダ限定：" + esc(String.join("、", folders));
        return "<div style=\"outline:2px dashed #ff2d78;outline-offset:2px;margin:6px 0;text-align:left\">"
                + "<div style=\"display:inline-block;background:#ff2d78;color:#fff;font:bold 11px/1.6 sans-serif;"
                + "padding:1px 8px;border-radius:3px;margin:2px\">" + label + "（" + scope + "）</div>"
                + (blank ? "<div style=\"color:#94a3b8;font:12px/1.6 sans-serif;padding:10px;text-align:center\">"
                        + "未設定（空欄のときは表示されません）</div>" : html)
                + "</div>";
    }

    private static String wrapTop(String area) {
        return area.isEmpty() ? "" : "<div class=\"member-area member-area-top " + SCOPE_CLASS + "\" "
                + "style=\"max-width:1060px;margin:auto;padding:12px 18px 0\">" + area + "</div>";
    }

    private static String wrapFp(String area) {
        return area.isEmpty() ? "" : "<div class=\"" + SCOPE_CLASS + "\" style=\"padding:8px\">" + area + "</div>";
    }

    /** {@code <style>} blocks written inside an HTML area, scoped like the page CSS. */
    private static String scopeStyleBlocks(String html) {
        Matcher m = STYLE_BLOCK.matcher(html);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(m.group(1) + scopeCss(m.group(2)) + m.group(3)));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * The operator's CSS limited to the HTML areas: every selector is prefixed with the areas'
     * class ({@code body} / {@code html} / {@code :root} become the area itself), so rules like
     * {@code body { background: … }} or {@code a { color: … }} don't recolor the design.
     * {@code @media} / {@code @supports} contents are scoped too; {@code @keyframes},
     * {@code @font-face} etc. are kept as written.
     */
    static String scopeCss(String css) {
        String src = css.replaceAll("(?s)/\\*.*?\\*/", "");
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < src.length()) {
            // skip stray ';' / whitespace between rules (e.g. "a{…};")
            while (i < src.length() && (src.charAt(i) == ';' || Character.isWhitespace(src.charAt(i)))) i++;
            if (i >= src.length()) break;
            int brace = indexOutsideQuotes(src, '{', i);
            int semi = indexOutsideQuotes(src, ';', i);
            if (brace < 0) {                       // trailing text / statement rules only
                out.append(src.substring(i));
                break;
            }
            String prelude = src.substring(i, brace).trim();
            if (prelude.startsWith("@") && semi >= 0 && semi < brace) {   // @import …; @charset …;
                out.append(src, i, semi + 1).append('\n');
                i = semi + 1;
                continue;
            }
            int close = matchingBrace(src, brace);
            String body = src.substring(brace + 1, close < 0 ? src.length() : close);
            if (prelude.startsWith("@")) {
                String name = prelude.toLowerCase(java.util.Locale.ROOT);
                boolean grouping = name.startsWith("@media") || name.startsWith("@supports")
                        || name.startsWith("@container") || name.startsWith("@layer");
                out.append(prelude).append(" {").append(grouping ? scopeCss(body) : body).append("}\n");
            } else if (!prelude.isEmpty()) {
                out.append(scopeSelectors(prelude)).append(" {").append(body).append("}\n");
            }
            i = close < 0 ? src.length() : close + 1;
        }
        return out.toString();
    }

    private static String scopeSelectors(String selectors) {
        String scope = "." + SCOPE_CLASS;
        List<String> parts = new java.util.ArrayList<>();
        int depth = 0, start = 0;
        for (int k = 0; k < selectors.length(); k++) {
            char c = selectors.charAt(k);
            if (c == '(' || c == '[') depth++;
            else if (c == ')' || c == ']') depth--;
            else if (c == ',' && depth == 0) { parts.add(selectors.substring(start, k)); start = k + 1; }
        }
        parts.add(selectors.substring(start));
        StringBuilder b = new StringBuilder();
        for (String part : parts) {
            String sel = part.trim();
            if (sel.isEmpty()) continue;
            boolean compound = false;                          // body.x (not body .x)
            Matcher root = ROOT_SELECTOR.matcher(sel);
            if (root.lookingAt()) {
                compound = root.end() < sel.length() && !Character.isWhitespace(sel.charAt(root.end() - 1));
                sel = sel.substring(root.end()).trim();
            }
            if (b.length() > 0) b.append(", ");
            if (sel.isEmpty()) b.append(scope);
            else if (compound) b.append(scope).append(sel);
            else b.append(scope).append(' ').append(sel);
        }
        return b.toString();
    }

    /** Leading {@code html} / {@code body} / {@code :root} (possibly chained: {@code html body}). */
    private static final Pattern ROOT_SELECTOR = Pattern.compile("(?i)(?:(?:html|body|:root)(?![\\w-])\\s*)+");

    private static int indexOutsideQuotes(String s, char target, int from) {
        char quote = 0;
        for (int k = from; k < s.length(); k++) {
            char c = s.charAt(k);
            if (quote != 0) {
                if (c == '\\') k++;
                else if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') quote = c;
            else if (c == target) return k;
        }
        return -1;
    }

    /** Index of the '}' closing the '{' at {@code open}, or -1 when unclosed. */
    private static int matchingBrace(String s, int open) {
        int depth = 0;
        char quote = 0;
        for (int k = open; k < s.length(); k++) {
            char c = s.charAt(k);
            if (quote != 0) {
                if (c == '\\') k++;
                else if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') quote = c;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return k;
        }
        return -1;
    }

    /** Inserts {@code add} right after the first {@code marker} at or after {@code from}. */
    private static String insertAfter(String html, String marker, int from, String add) {
        if (add.isEmpty() || from < 0) return html;
        int at = html.indexOf(marker, from);
        if (at < 0) return html;
        at += marker.length();
        return html.substring(0, at) + add + html.substring(at);
    }

    /** Inserts {@code add} right before the last {@code marker}. */
    private static String insertBefore(String html, String marker, String add) {
        if (add.isEmpty()) return html;
        int at = html.lastIndexOf(marker);
        return at < 0 ? html + add : html.substring(0, at) + add + html.substring(at);
    }

    /** Links between the design's pages (index.html, ../friends.html …) → {@code linkForCode}. */
    private static String rewriteLinks(String html, Function<String, String> linkForCode) {
        Matcher m = LINK.matcher(html);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String code = FILE_TO_CODE.get(m.group(1));
            String href = code == null ? m.group() : "href=\"" + esc(linkForCode.apply(code)) + "\"";
            m.appendReplacement(out, Matcher.quoteReplacement(href));
        }
        m.appendTail(out);
        return out.toString();
    }

    private String load(String name) {
        return resources.computeIfAbsent(name, n -> {
            try (InputStream in = new ClassPathResource(DIR + n).getInputStream()) {
                return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("missing " + DIR + n, e);
            }
        });
    }

    private static String esc(String v) {
        return HtmlUtils.htmlEscape(v == null ? "" : v, "UTF-8");
    }
}
