package com.crm.controller;

import com.crm.entity.HomeHtml;
import com.crm.service.HomeHtmlService;
import com.crm.service.PublicSiteService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * Root-path handler. If an admin has marked one of the {@link HomeHtml} variants active,
 * we serve its raw HTML directly so https://&lt;host&gt;/ shows a landing page rather than
 * exposing the management UI. Otherwise serve the public pre-login top page (18歳以上確認 /
 * 会員登録 / ログイン — {@link PublicSiteService}); the admin UI stays at /manager/login.
 */
@Controller
public class HomeController {

    private final HomeHtmlService homeHtmlService;
    private final PublicSiteService publicSiteService;

    public HomeController(HomeHtmlService homeHtmlService, PublicSiteService publicSiteService) {
        this.homeHtmlService = homeHtmlService;
        this.publicSiteService = publicSiteService;
    }

    @GetMapping("/")
    public Object root(HttpServletRequest request) {
        Optional<HomeHtml> active = homeHtmlService.findActive();
        if (active.isPresent() && active.get().getHtmlContent() != null
                && !active.get().getHtmlContent().isEmpty()) {
            // Serve the raw HTML as a ResponseEntity so Spring does NOT treat it as a view
            // name. text/html UTF-8 so kanji in the landing page renders correctly.
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(active.get().getHtmlContent());
        }
        // CsrfInterceptor exposes the session token as a request attribute for the page's forms.
        Object csrf = request.getAttribute("_csrf");
        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(publicSiteService.renderTop(csrf == null ? null : csrf.toString()));
    }
}
