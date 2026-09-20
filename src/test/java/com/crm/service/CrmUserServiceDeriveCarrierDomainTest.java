package com.crm.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "LINE" is reserved by the user list's キャリア=LINE badge/filter (client-reported bug,
 * 2026-09-21) — a manually-typed "LINE" in the carrierDomain free-text field must not render
 * as a misleading "@LINE" badge for a customer who isn't actually LINE-linked.
 */
class CrmUserServiceDeriveCarrierDomainTest {

    private static String derive(String carrierDomain, String email) throws Exception {
        Method m = CrmUserService.class.getDeclaredMethod("deriveCarrierDomainIfBlank", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, carrierDomain, email);
    }

    @Test
    void literalLine_fallsBackToEmailDomain() throws Exception {
        assertThat(derive("LINE", "user@docomo.ne.jp")).isEqualTo("docomo.ne.jp");
    }

    @Test
    void literalLine_caseInsensitive_fallsBackToEmailDomain() throws Exception {
        assertThat(derive("line", "user@docomo.ne.jp")).isEqualTo("docomo.ne.jp");
    }

    @Test
    void literalLine_withNoEmail_resultsInNull() throws Exception {
        assertThat(derive("LINE", null)).isNull();
    }

    @Test
    void realDomain_isKeptAsIs() throws Exception {
        assertThat(derive("softbank.ne.jp", "user@gmail.com")).isEqualTo("softbank.ne.jp");
    }

    @Test
    void blank_derivesFromEmail() throws Exception {
        assertThat(derive("", "user@icloud.com")).isEqualTo("icloud.com");
    }
}
