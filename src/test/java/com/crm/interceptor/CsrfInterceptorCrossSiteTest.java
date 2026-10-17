package com.crm.interceptor;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsrfInterceptorCrossSiteTest {

    private static MockHttpServletRequest crossSiteGet() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/manager/users/1/thread");
        req.addHeader("Sec-Fetch-Site", "cross-site");
        req.addHeader("Sec-Fetch-Mode", "navigate");
        req.addHeader("Sec-Fetch-Dest", "document");
        return req;
    }

    @Test
    void crossSiteLinkWithoutCookieReloadsWithoutMakingASession() throws Exception {
        MockHttpServletRequest req = crossSiteGet();
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertFalse(new CsrfInterceptor().preHandle(req, res, null));
        assertNull(req.getSession(false));
        assertTrue(res.getContentAsString().contains("location.replace(location.href)"));
    }

    @Test
    void sameSiteOrWithCookieIsUnchanged() throws Exception {
        MockHttpServletRequest withCookie = crossSiteGet();
        withCookie.setRequestedSessionId("abc");
        assertTrue(new CsrfInterceptor().preHandle(withCookie, new MockHttpServletResponse(), null));
        assertNotNull(withCookie.getSession(false));

        MockHttpServletRequest sameSite = new MockHttpServletRequest("GET", "/manager/users/1/thread");
        sameSite.addHeader("Sec-Fetch-Site", "same-origin");
        assertTrue(new CsrfInterceptor().preHandle(sameSite, new MockHttpServletResponse(), null));

        MockHttpServletRequest noHeaders = new MockHttpServletRequest("GET", "/");
        assertTrue(new CsrfInterceptor().preHandle(noHeaders, new MockHttpServletResponse(), null));
    }
}
