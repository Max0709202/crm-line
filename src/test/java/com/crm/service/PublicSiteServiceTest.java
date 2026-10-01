package com.crm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Pre-login top page (本ドメイン /): the client design with its tags filled from 番組デザイン設定. */
class PublicSiteServiceTest {

    private SiteDesignService design;
    private PublicSiteService svc;

    @BeforeEach
    void setUp() {
        design = mock(SiteDesignService.class);
        when(design.getSiteName()).thenReturn("テストサイト");
        when(design.getConfiguredSiteName()).thenReturn("テストサイト");
        when(design.getContactEmail()).thenReturn("info@avu74g.jp");
        when(design.getFooterNote()).thenReturn(SiteDesignService.DEFAULT_FOOTER_NOTE);
        svc = new PublicSiteService(design);
    }

    @Test
    void defaultDesignFillsEveryTag() {
        String html = svc.renderTop("tok123");

        assertThat(html).doesNotContainPattern("%(sitename|brand|top_picture|top_blur|year|csrf|contact_mailto|footer)%");
        assertThat(html).contains("<title>テストサイト | 無料会員登録</title>",
                "<p class=\"sysft-copy\">© " + Year.now().getValue() + " テストサイト</p>",
                "<p class=\"sysft-note\">18歳未満の方および高校生の方のご利用はお断りしています。</p>",
                "<a href=\"#login\">会員ログイン</a>",
                "<input type=\"hidden\" name=\"_csrf\" value=\"tok123\">",
                "action=\"/member/register\"", "action=\"/member/login\"",
                "href=\"mailto:info@avu74g.jp\">お問い合わせ</a>",
                // no logo → the design's heart icon + site name
                "<use href=\"#i-heart\"/></svg><span>テストサイト</span>",
                // default images with their WebP variants
                "/member/images/main_pc.webp", "/member/images/main_sp.jpg",
                "url(/member/images/main_blur.jpg)");
        // exactly one footer (a tag named inside an HTML comment would render a second copy)
        assertThat(html.indexOf("class=\"sysft\"")).isEqualTo(html.lastIndexOf("class=\"sysft\""));
        assertThat(html.split("name=\"_csrf\"", -1)).hasSize(3); // register + login forms only
        // footer links go to the 番組デザイン設定 pages
        assertThat(html).contains("href=\"/page/faq\"", "href=\"/page/age\"", "href=\"/page/tokushoho\"",
                "href=\"/page/privacy\"", "href=\"/page/terms\"", "href=\"/page/price\"");
    }

    @Test
    void featurePhoneDesignFillsEveryTag() {
        when(design.getTopHtml()).thenReturn("<p>operator html</p>");   // ガラケー keeps its own design
        String html = svc.renderTopFp("tok123");

        assertThat(html).doesNotContainPattern("%(sitename|brand|top_picture|top_blur|year|csrf|contact_mailto|footer)%");
        assertThat(html).startsWith("<?xml").doesNotContain("operator html", "class=\"sysft\"", "data:image");
        assertThat(html).contains("<title>テストサイト</title>",
                "&#169; " + Year.now().getValue() + " テストサイト",
                "<img src=\"/member/images/main_fp.jpg\" width=\"240\" height=\"180\"",
                "action=\"/member/register\"", "action=\"/member/login\"",
                "<input type=\"hidden\" name=\"_csrf\" value=\"tok123\" />",
                "href=\"mailto:info@avu74g.jp\" accesskey=\"8\"",
                "href=\"/page/age\"", "href=\"/page/tokushoho\"", "href=\"/page/privacy\"",
                "href=\"/page/terms\"", "href=\"/page/price\"");
        assertThat(html.split("name=\"_csrf\"", -1)).hasSize(3);
    }

    @Test
    void siteNameIsEscapedAndNotExpandedTwice() {
        when(design.getSiteName()).thenReturn("<b>A&B</b> %year%");
        String html = svc.renderTop("t");
        assertThat(html).contains("&lt;b&gt;A&amp;B&lt;/b&gt; %year%").doesNotContain("<b>A&B</b>");
    }

    @Test
    void uploadedLogoAndImagesReplaceTheDefaults() {
        when(design.getLogoUrl()).thenReturn("/img/7");
        when(design.getTopImagePcUrl()).thenReturn("/img/8");
        when(design.getTopImageSpUrl()).thenReturn("/img/9");
        String html = svc.renderTop("t");

        assertThat(html).contains("<a class=\"brand\" href=\"/\" aria-label=\"テストサイト トップ\"><img src=\"/img/7\" alt=\"テストサイト\"></a>",
                "srcset=\"/img/9\"", "<img src=\"/img/8\"", "url(\"/img/8\")");
        assertThat(html).doesNotContain("main_pc.jpg", "main_sp.jpg", "main_blur.jpg", "#i-heart\"/></svg><span>");
    }

    @Test
    void contactLinkIsInertWithoutAnAddress() {
        when(design.getContactEmail()).thenReturn(null);
        assertThat(svc.contactMailto()).isEqualTo("#");
        assertThat(svc.renderTop("t")).contains("href=\"#\">お問い合わせ</a>");
    }

    @Test
    void operatorHtmlIsUsedAndTheFooterCannotBeRemoved() {
        when(design.getTopHtml()).thenReturn("<html><body><h1>%sitename%</h1></BODY></html>");
        String html = svc.renderTop("t");
        assertThat(html).startsWith("<html><body><h1>テストサイト</h1><footer class=\"sysft\" id=\"footer\">")
                .contains("href=\"/page/tokushoho\">特定商取引法に基づく表記</a>")
                .endsWith("</footer>\n</BODY></html>")
                .doesNotContain("あなたは18歳以上ですか"); // the default design is not used

        // an explicit %footer% is honoured (once), not duplicated
        when(design.getTopHtml()).thenReturn("<body>%footer%<p>after</p></body>");
        String h2 = svc.renderTop("t");
        assertThat(h2.indexOf("class=\"sysft\"")).isEqualTo(h2.lastIndexOf("class=\"sysft\""));
        assertThat(h2).contains("</footer><p>after</p>");
    }

    @Test
    void footerNeverShowsTheBareDomain() {
        when(design.getSiteName()).thenReturn("avu74g.jp");          // fallback when no site name is set
        when(design.getConfiguredSiteName()).thenReturn("");
        String footer = svc.footerHtml("#login");
        assertThat(footer).doesNotContain(">avu74g.jp<", " avu74g.jp</p>", "class=\"sysft-brand\"")
                .contains("<p class=\"sysft-copy\">© " + Year.now().getValue() + "</p>");
    }

    @Test
    void footerNoteIsEscapedWithLineBreaks() {
        when(design.getFooterNote()).thenReturn("届出番号 123\n<script>x</script>");
        assertThat(svc.footerHtml("/#login"))
                .contains("届出番号 123<br>&lt;script&gt;x&lt;/script&gt;", "<a href=\"/#login\">会員ログイン</a>");
        when(design.getFooterNote()).thenReturn("");
        assertThat(svc.footerHtml("#login")).doesNotContain("sysft-note\">");
    }
}
