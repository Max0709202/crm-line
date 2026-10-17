package com.crm.controller;

import com.crm.entity.CrmUser;
import com.crm.entity.GeneralHtml;
import com.crm.repository.CrmUserRepository;
import com.crm.service.GeneralHtmlService;
import com.crm.service.UserActivityService;
import com.crm.util.ClientIpResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Optional;

/**
 * 全般HTML's public page ({@code /h/{urlKey}}): the slot's HTML for anyone — no login, any folder.
 * A view that can be tied to a user (the {@code ?u=} of a personalised link, else a logged-in
 * member) is recorded for アクセスユーザー; see {@link GeneralHtmlService}.
 */
@Controller
public class GeneralHtmlController {

    private final GeneralHtmlService generalHtmlService;
    private final UserActivityService userActivityService;
    private final CrmUserRepository userRepository;

    public GeneralHtmlController(GeneralHtmlService generalHtmlService, UserActivityService userActivityService,
                                 CrmUserRepository userRepository) {
        this.generalHtmlService = generalHtmlService;
        this.userActivityService = userActivityService;
        this.userRepository = userRepository;
    }

    /** LINE用 members stay logged in by cookie after the session ends (optional: session only without it). */
    private com.crm.service.MemberAutoLoginService autoLoginService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAutoLoginService(com.crm.service.MemberAutoLoginService autoLoginService) { this.autoLoginService = autoLoginService; }

    @GetMapping(GeneralHtmlService.PATH + "/{key}")
    public ResponseEntity<String> view(@PathVariable String key,
                                       @RequestParam(name = "u", required = false) String u,
                                       HttpServletRequest request) {
        Optional<GeneralHtml> g = generalHtmlService.findByKey(key);
        if (!g.isPresent() || g.get().getHtml() == null || g.get().getHtml().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(new MediaType(MediaType.TEXT_HTML, java.nio.charset.StandardCharsets.UTF_8))
                    .body("<!doctype html><html lang=\"ja\"><head><meta charset=\"utf-8\"><title>ページが見つかりません</title></head>"
                            + "<body><p>ページが見つかりません</p></body></html>");
        }
        Optional<CrmUser> viewer = generalHtmlService.resolveViewer(u);
        if (!viewer.isPresent()) viewer = loggedInMember(request);
        // An operator's 管理者プレビュー of a member is not that member's view
        if (viewer.isPresent() && !MemberSiteController.preview(request.getSession(false))) {
            userActivityService.touchLastLogin(viewer.get(), GeneralHtmlService.source(g.get().getSlotNo()),
                    ClientIpResolver.resolve(request), request.getHeader("User-Agent"));
        }
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, java.nio.charset.StandardCharsets.UTF_8))
                // the ?u= stays on this site (not sent to images / links inside the HTML)
                .header("Referrer-Policy", "no-referrer")
                .header("X-Robots-Tag", "noindex")
                .body(g.get().getHtml());
    }

    private Optional<CrmUser> loggedInMember(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object id = session == null ? null : session.getAttribute(PublicSiteController.SESSION_MEMBER_ID);
        if (id instanceof Long) return userRepository.findById((Long) id);
        return autoLoginService == null ? Optional.empty() : autoLoginService.resolveRemembered(request);
    }
}
