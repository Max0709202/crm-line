package com.crm.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    void scopeCss_scopesInsideMediaButKeepsKeyframes() {
        String out = MemberPageService.scopeCss(
                "@media (max-width:600px){ h1 { font-size:14px } }\n"
                + "@keyframes blink { from { opacity:0 } to { opacity:1 } }");
        assertThat(out)
                .contains("@media (max-width:600px) {.member-free h1 {")
                .contains("@keyframes blink { from { opacity:0 } to { opacity:1 } }");
    }
}
