package com.crm.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemberPageServiceTest {

    @Test
    void scopeCss_limitsRulesToTheHtmlAreas() {
        String out = MemberPageService.scopeCss(
                "body { background:#000; color:#fff }\n"
                + "a, .campaign > b { color:red }\n"
                + "body.x p { margin:0 }\n"
                + "html body .y { color:blue }\n"
                + "/* comment { } */\n"
                + ".q::before { content:\"{\" }");
        assertThat(out)
                .contains(".member-free {")
                .contains(".member-free a, .member-free .campaign > b {")
                .contains(".member-free.x p {")
                .contains(".member-free .y {")
                .contains(".member-free .q::before { content:\"{\" }")
                .doesNotContain("comment")
                .doesNotContain("body")
                .doesNotContain("html");
    }

    @Test
    void scopeCss_ignoresStraySemicolonsBetweenRules() {
        assertThat(MemberPageService.scopeCss("a { color:red };\n; .b { margin:0 }"))
                .contains(".member-free a {")
                .contains(".member-free .b {")
                .doesNotContain(";  .b");
    }

    @Test
    void scopeCss_scopesInsideMediaButKeepsKeyframes() {
        String out = MemberPageService.scopeCss(
                "@media (max-width:600px){ h1 { font-size:14px } }\n"
                + "@keyframes blink { from { opacity:0 } to { opacity:1 } }");
        assertThat(out)
                .contains("@media (max-width:600px) {.member-free h1 {")
                .contains("@keyframes blink { from { opacity:0 } to { opacity:1 } }");
    }

    @Test
    void pointPurchaseFollowsPaymentSettingsOrderAndNames() {
        SiteDesignService site = mock(SiteDesignService.class);
        List<SiteDesignService.Slot> slots = new ArrayList<>();
        for (String code : SiteDesignService.MEMBER_PAGES.keySet()) {
            slots.add(new SiteDesignService.Slot(code, code, "", "", "",
                    Collections.<String>emptyList(), Collections.<String>emptyList()));
        }
        when(site.getSlots()).thenReturn(slots);
        when(site.getConfiguredSiteName()).thenReturn("テスト");
        PaymentSettingService pay = mock(PaymentSettingService.class);
        List<PaymentSettingService.Method> methods = new ArrayList<>();
        methods.add(method("bank", "振込", true, new PaymentSettingService.Plan(true, 2000, 2200)));
        methods.add(method("credit", "カード", true, new PaymentSettingService.Plan(true, 1000, 1000)));
        methods.add(method("emoney", "電子マネー", false, new PaymentSettingService.Plan(true, 500, 500)));
        when(pay.getMethods(null)).thenReturn(methods);

        String html = new MemberPageService(site, pay).renderPreview("points", "sp", false, c -> c + ".html");
        assertThat(html).contains("<h3 class=\"methodtitle\">振込</h3>")
                .contains("2,200ポイント</b><span>2,000円(税込)")
                .doesNotContain("電子マネー")
                .doesNotContain("%plans%");
        assertThat(html.indexOf(">振込<")).isLessThan(html.indexOf(">カード<"));
    }

    @Test
    void everyPageShowsBothHtmlAreasAndNoLeftoverTags() {
        SiteDesignService site = mock(SiteDesignService.class);
        List<SiteDesignService.Slot> slots = new ArrayList<>();
        for (String code : SiteDesignService.MEMBER_PAGES.keySet()) {
            slots.add(new SiteDesignService.Slot(code, code, "<p>TOP-" + code + "</p>", "<p>BOTTOM-" + code + "</p>",
                    "p { color:red }", Collections.<String>emptyList(), Collections.<String>emptyList()));
        }
        when(site.getSlots()).thenReturn(slots);
        when(site.getConfiguredSiteName()).thenReturn("");
        when(site.getLogoUrl()).thenReturn("/img/6");
        PaymentSettingService pay = mock(PaymentSettingService.class);
        when(pay.getMethods(null)).thenReturn(new ArrayList<>());
        MemberPageService svc = new MemberPageService(site, pay);

        for (String device : Arrays.asList("pc", "sp", "fp")) {
            for (String code : MemberPageService.BAR_TITLES.keySet()) {
                String html = svc.renderPreview(code, device, false, c -> c + ".html");
                String where = device + "/" + code;
                assertThat(html).as(where)
                        .contains("<p>TOP-" + code + "</p>", "<p>BOTTOM-" + code + "</p>", ".member-free p {",
                                "<img src=\"/img/6\"")
                        .doesNotContain("%HTML%", "%main%", "%title%", "%plans%", "%sitename%", "%sitelogo%");
                // the top area sits after the page's header, the bottom one after the top one
                assertThat(html.indexOf("TOP-" + code)).as(where).isLessThan(html.indexOf("BOTTOM-" + code));
            }
        }
    }

    @Test
    void featurePhonePagesUseTheirOwnDesignWithLogoAboveTheGreenHeader() {
        SiteDesignService site = mock(SiteDesignService.class);
        List<SiteDesignService.Slot> slots = new ArrayList<>();
        for (String code : SiteDesignService.MEMBER_PAGES.keySet()) {
            slots.add(new SiteDesignService.Slot(code, code, "<p>TOP</p>", "<p>BOTTOM</p>", "",
                    Collections.<String>emptyList(), Collections.<String>emptyList()));
        }
        when(site.getSlots()).thenReturn(slots);
        when(site.getConfiguredSiteName()).thenReturn("サイト名");
        when(site.getLogoUrl()).thenReturn("/img/6");
        PaymentSettingService pay = mock(PaymentSettingService.class);
        List<PaymentSettingService.Method> methods = new ArrayList<>();
        methods.add(method("bank", "振込", true, new PaymentSettingService.Plan(true, 2000, 2200)));
        when(pay.getMethods(null)).thenReturn(methods);
        MemberPageService svc = new MemberPageService(site, pay);

        for (String code : MemberPageService.FP_PAGES) {
            String html = svc.renderPreview(code, "fp", false, c -> c + ".html");
            assertThat(html).as(code).doesNotContain("class=\"bar\"", "---P", "%plans%")   // not the スマホ layout
                    .contains("<div class=\"logo\"><img src=\"/img/6\"");
            assertThat(html.indexOf("<div class=\"logo\">")).as(code).isLessThan(html.indexOf("<header>"));
            assertThat(html.indexOf("サイト名")).as(code).isLessThan(html.indexOf("<header>"));
            if (html.contains("<div class=\"f\">")) {
                assertThat(html.indexOf("BOTTOM")).as(code).isLessThan(html.indexOf("<div class=\"f\">"));
            }
        }
        assertThat(svc.renderPreview("points", "fp", false, c -> c + ".html"))
                .contains("<div class=\"ttl\">振込</div><div class=\"m\"><a href=\"#\">2,200ポイント　¥2,000</a></div>");
        assertThat(svc.renderPreview("point_table", "fp", false, c -> c + ".html")).contains("メール送信：55ポイント");
        // pages without a ガラケー design fall back to the スマホ one
        assertThat(svc.renderPreview("search", "fp", false, c -> c + ".html")).contains("class=\"bar\"");
    }

    private static PaymentSettingService.Method method(String code, String label, boolean shown,
                                                       PaymentSettingService.Plan plan) {
        return new PaymentSettingService.Method(code, label, shown, Arrays.asList(plan));
    }
}
