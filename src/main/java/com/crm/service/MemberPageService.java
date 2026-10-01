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
    private static final Pattern TAG = Pattern.compile("%(sitename|sitelogo|id|name|point|toname)%");

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
        if (!BAR_TITLES.containsKey(code)) code = "menu";
        boolean fp = "fp".equals(device) && FP_PAGES.contains(code);
        SiteDesignService.Slot slot = slotFor(code);

        String html;
        if (fp) {
            html = load("fp/" + code + ".html");
            if ("points".equals(code)) html = html.replace("%plans%", fpPointPlans());
        } else {
            html = load("layout.html")
                    .replace("%title%", BAR_TITLES.get(code))
                    .replace("%main%", "points".equals(code)
                            ? load("points.html").replace("%plans%", pointPlans())
                            : load(code + ".html"));
        }
        html = fillTags(html);

        String top = area("上部HTML", slot.getTopHtml(), slot.getTopFolders(), markAreas);
        String bottom = area("下部HTML", slot.getBottomHtml(), slot.getBottomFolders(), markAreas);
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

        String css = slot.getCss();
        if (css != null && !css.trim().isEmpty()) {
            html = insertBefore(html, "</head>", "<style>\n" + scopeCss(css) + "\n</style>");
        }
        return rewriteLinks(html, linkForCode);
    }

    /** ポイント購入: each shown payment method (saved order and name) with its plans. */
    private String pointPlans() {
        StringBuilder b = new StringBuilder();
        for (PaymentSettingService.Method m : paymentSettingService.getMethods(null)) {
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

    /** The design's member tags, with sample values (member accounts aren't built yet). */
    private String fillTags(String html) {
        String logo = siteDesignService.getLogoUrl();
        Map<String, String> values = new HashMap<>();
        values.put("sitename", esc(siteDesignService.getConfiguredSiteName()));   // never the bare domain
        values.put("sitelogo", logo == null ? ""
                : "<img src=\"" + esc(logo) + "\" alt=\"\" style=\"max-height:40px;max-width:100%\">");
        values.put("id", "000123");
        values.put("name", "サンプル");
        values.put("point", "1,000");
        values.put("toname", "まい");
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
