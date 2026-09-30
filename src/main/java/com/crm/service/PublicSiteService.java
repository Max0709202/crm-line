package com.crm.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders the public pre-login top page (本ドメイン / when no 本ドメイン表示設定 pattern is
 * active). The page is the operator's HTML from 番組デザイン設定, or — when none is saved — the
 * client-supplied design in {@code site/top.html}. These tags are filled in a single pass (so a
 * site name containing "%year%" etc. is never expanded twice):
 * <pre>
 *   %sitename%        site name (HTML-escaped)
 *   %brand%           header logo image, or the default heart icon + site name
 *   %top_picture%     top image (PC / スマホ), default images unless replaced
 *   %top_blur%        background behind the top image
 *   %year%            current year
 *   %csrf%            CSRF token for the register / login forms
 *   %contact_mailto%  mailto: link for お問い合わせ
 *   %footer%          the fixed site footer (see {@link #footerHtml})
 * </pre>
 * The footer is fixed: if the operator's HTML drops %footer%, it is inserted before
 * {@code </body>} anyway, so the legally required links and notes can't be edited away.
 */
@Service
public class PublicSiteService {

    private static final String TOP_TEMPLATE = "site/top.html";
    private static final String DEFAULT_IMAGES = "/member/images/";
    private static final Pattern TAG = Pattern.compile(
            "%(sitename|brand|top_picture|top_blur|year|csrf|contact_mailto|footer)%");
    private static final Pattern BODY_END = Pattern.compile("(?i)</body\\s*>");

    private final SiteDesignService siteDesignService;
    private volatile String defaultTopTemplate;

    public PublicSiteService(SiteDesignService siteDesignService) {
        this.siteDesignService = siteDesignService;
    }

    public String renderTop(String csrfToken) {
        String name = siteDesignService.getSiteName();
        String logo = siteDesignService.getLogoUrl();
        String pc = siteDesignService.getTopImagePcUrl();
        String sp = siteDesignService.getTopImageSpUrl();

        Map<String, String> values = new HashMap<>();
        values.put("sitename", esc(name));
        values.put("brand", brandHtml(name, logo));
        values.put("top_picture", pictureHtml(pc, sp));
        values.put("top_blur", pc != null ? cssUrl(pc) : DEFAULT_IMAGES + "main_blur.jpg");
        values.put("year", String.valueOf(Year.now().getValue()));
        values.put("csrf", esc(csrfToken == null ? "" : csrfToken));
        values.put("contact_mailto", esc(contactMailto()));
        values.put("footer", footerHtml("#login"));

        String template = siteDesignService.getTopHtml();
        if (template == null) template = getDefaultTopTemplate();
        if (!template.contains("%footer%")) {
            Matcher end = BODY_END.matcher(template);
            int at = -1;
            while (end.find()) at = end.start();
            template = at < 0 ? template + "\n%footer%\n"
                    : template.substring(0, at) + "%footer%\n" + template.substring(at);
        }

        Matcher m = TAG.matcher(template);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(values.get(m.group(1))));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * The fixed site footer shared by the top page and the /page/* pages: brand, links to the
     * 番組デザイン設定 pages, お問い合わせ, 会員ログイン, the operator's note, copyright. Styles are
     * scoped to {@code .sysft} so it looks the same whatever CSS the surrounding page has.
     *
     * @param loginHref "#login" on the top page (opens its login dialog), "/#login" elsewhere
     */
    public String footerHtml(String loginHref) {
        String name = esc(siteDesignService.getSiteName());
        StringBuilder b = new StringBuilder();
        b.append("<footer class=\"sysft\" id=\"footer\">\n<style>")
         .append(".sysft{background:#0E2B45;color:#B9CCDC;padding:52px 24px calc(40px + env(safe-area-inset-bottom,0px));font-size:13px;line-height:1.8}")
         .append(".sysft a{color:inherit;text-decoration:none}")
         .append(".sysft-in{max-width:1120px;margin:0 auto}")
         .append(".sysft-brand{font-weight:900;font-size:20px;color:#fff}")
         .append(".sysft-links{display:flex;flex-wrap:wrap;gap:8px 24px;margin:20px 0 26px}")
         .append(".sysft-links a{padding:4px 0}")
         .append(".sysft-links a:hover{color:#fff;text-decoration:underline;text-underline-offset:3px}")
         .append(".sysft-note{margin:0;line-height:1.7}.sysft-copy{margin:8px 0 0;opacity:.8}")
         .append("</style>\n  <div class=\"sysft-in\">\n    <div class=\"sysft-brand\">").append(name).append("</div>\n")
         .append("    <nav class=\"sysft-links\" aria-label=\"サイト情報\">\n")
         .append("      <a href=\"/\">ホーム</a>\n");
        for (Map.Entry<String, String> p : SiteDesignService.PAGES.entrySet()) {
            b.append("      <a href=\"/page/").append(p.getKey()).append("\">").append(esc(p.getValue())).append("</a>\n");
        }
        b.append("      <a href=\"").append(esc(contactMailto())).append("\">お問い合わせ</a>\n")
         .append("      <a href=\"").append(esc(loginHref)).append("\">会員ログイン</a>\n")
         .append("    </nav>\n");
        String note = siteDesignService.getFooterNote();
        if (note != null && !note.trim().isEmpty()) {
            b.append("    <p class=\"sysft-note\">").append(esc(note.trim()).replace("\n", "<br>")).append("</p>\n");
        }
        b.append("    <p class=\"sysft-copy\">© ").append(Year.now().getValue()).append(' ').append(name).append("</p>\n")
         .append("  </div>\n</footer>");
        return b.toString();
    }

    /** mailto: link for お問い合わせ (raw, not HTML-escaped), "#" when no address is known. */
    public String contactMailto() {
        String email = siteDesignService.getContactEmail();
        return email == null ? "#" : "mailto:" + email;
    }

    /** The bundled client design — what 番組デザイン設定 shows / restores when nothing is saved. */
    public String getDefaultTopTemplate() {
        String t = defaultTopTemplate;
        if (t == null) {
            try (InputStream in = new ClassPathResource(TOP_TEMPLATE).getInputStream()) {
                t = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("missing " + TOP_TEMPLATE, e);
            }
            defaultTopTemplate = t;
        }
        return t;
    }

    /** Header brand: the uploaded logo if any, else the design's heart icon + site name. */
    static String brandHtml(String name, String logoUrl) {
        if (logoUrl != null) {
            return "<img src=\"" + esc(logoUrl) + "\" alt=\"" + esc(name) + "\">";
        }
        return "<svg aria-hidden=\"true\"><use href=\"#i-heart\"/></svg><span>" + esc(name) + "</span>";
    }

    /**
     * Top image. Each of PC / スマホ falls back to the bundled default (with its WebP variant)
     * independently; a replaced image is used as-is for its screen size.
     */
    static String pictureHtml(String pcUrl, String spUrl) {
        StringBuilder b = new StringBuilder("<picture>\n");
        if (spUrl != null) {
            b.append("        <source media=\"(max-width: 760px)\" srcset=\"").append(esc(spUrl)).append("\">\n");
        } else {
            b.append("        <source media=\"(max-width: 760px)\" srcset=\"").append(DEFAULT_IMAGES)
             .append("main_sp.webp\" type=\"image/webp\" width=\"992\" height=\"345\">\n");
            b.append("        <source media=\"(max-width: 760px)\" srcset=\"").append(DEFAULT_IMAGES)
             .append("main_sp.jpg\" width=\"992\" height=\"345\">\n");
        }
        String alt = "ビーチで寄り添うカップルと「HAPPY 理想の出会いはここに…」のメッセージ";
        if (pcUrl != null) {
            b.append("        <img src=\"").append(esc(pcUrl)).append("\" fetchpriority=\"high\" alt=\"\">\n");
        } else {
            b.append("        <source srcset=\"").append(DEFAULT_IMAGES).append("main_pc.webp\" type=\"image/webp\">\n");
            b.append("        <img src=\"").append(DEFAULT_IMAGES)
             .append("main_pc.jpg\" width=\"1501\" height=\"527\" fetchpriority=\"high\" alt=\"").append(alt).append("\">\n");
        }
        return b.append("      </picture>").toString();
    }

    private static String esc(String v) {
        return HtmlUtils.htmlEscape(v == null ? "" : v, "UTF-8");
    }

    /** Value for CSS url(...): quote it so spaces/parentheses in an image URL can't break out. */
    private static String cssUrl(String url) {
        return "\"" + url.replace("\\", "").replace("\"", "%22").replace("\n", "") + "\"";
    }
}
