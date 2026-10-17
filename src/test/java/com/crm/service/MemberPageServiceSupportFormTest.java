package com.crm.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberPageServiceSupportFormTest {

    @Test
    void formPostsIdToSupportWithCsrfAndBack() {
        String f = MemberPageService.supportForm("<input type=\"hidden\" name=\"_csrf\" value=\"tok\">", "/member/points", false);
        assertTrue(f.contains("action=\"/member/support/id\""));
        assertTrue(f.contains("name=\"_csrf\" value=\"tok\""));
        assertTrue(f.contains("name=\"back\" value=\"/member/points\""));
        assertTrue(f.contains("name=\"id\""));
        assertFalse(f.contains("return false"));
    }

    @Test
    void backIsEscapedAndSampleNeverSubmits() {
        String f = MemberPageService.supportForm("", "/member/points\"><script>", true);
        assertFalse(f.contains("<script>"));
        assertTrue(f.contains("onsubmit=\"return false\""));
    }

    @Test
    void resultIsShownInTheFormWithTheIdsAPageScriptUses() {
        String f = MemberPageService.supportForm("", "/member/points", false, "送信しました。", null);
        assertTrue(f.contains("id=\"supportForm\""));
        assertTrue(f.contains("id=\"message\" name=\"id\""));
        assertTrue(f.contains("id=\"formError\""));
        assertTrue(f.contains("送信しました。"));

        String e = MemberPageService.supportForm("", "/member/points", false, null, "IDを入力してください<b>");
        assertTrue(e.contains("IDを入力してください&lt;b&gt;</div>"));
        assertFalse(e.contains("support-id-done"));
    }
}
