package com.crm.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** メールテンプレート設定 — tag replacement. */
class MailTemplateServiceTest {

    @Test
    void fill_replacesKnownTagsAndKeepsUnknownOnes() {
        Map<String, String> v = new HashMap<>();
        v.put("%sitename%", "Happy");
        v.put("%id%", "10054");
        v.put("%verify_url%", "https://example.jp/member/confirm?token=a$b");
        String out = MailTemplateService.fill("%sitename%へ ID:%id% %verify_url% %typo%", v);
        assertThat(out).isEqualTo("Happyへ ID:10054 https://example.jp/member/confirm?token=a$b %typo%");
    }

    @Test
    void defaultProvisional_usesOnlyProvidedTags() {
        assertThat(MailTemplateService.DEFAULT_PROVISIONAL_BODY)
                .contains("%verify_url%", "%id%", "%sitename%", "%expire%");
    }

    @Test
    void renamePaymentTags_mapsOldNamesOnly_andIsIdempotent() {
        String old = "%name%様 %amount% / %add_point% / %payment_method% / %paid_at% / %order_no% %login_url%";
        String renamed = MailTemplateService.renamePaymentTags(old);
        assertThat(renamed).isEqualTo("%name%様 %pay_amount% / %pay_point% / %pay_method% / %pay_date% / %pay_no% %login_url%");
        assertThat(MailTemplateService.renamePaymentTags(renamed)).isEqualTo(renamed);
    }
}
