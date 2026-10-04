package com.crm.controller;

import com.crm.entity.CrmUser;
import com.crm.repository.CrmUserRepository;
import com.crm.service.MemberPageService;
import com.crm.service.MemberRegistrationService;
import com.crm.service.PublicSiteService;
import com.crm.service.SiteDesignService;
import com.crm.service.UserPointService;
import com.crm.util.ClientIpResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.HtmlUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * Public member (番組) site pages outside the top page: the footer pages edited on
 * 番組デザイン設定 (/page/{code}), and member registration:
 * <ul>
 *   <li>POST /member/register — the top page's 無料会員登録 form; creates a 仮登録 (PENDING) user and
 *       mails the 本登録 link (see {@link MemberRegistrationService}), then shows 仮登録完了;</li>
 *   <li>GET /member/confirm?token=… — the mailed link; the user becomes ACTIVE and 本登録完了 is shown,
 *       whose プロフィール登録 button opens /member/profile (プロフ編集) for that member.</li>
 * </ul>
 * Member login is not built yet, so /member/login still answers with a 準備中 page.
 */
@Controller
public class PublicSiteController {

    /** Member who just completed 本登録 in this browser (for プロフィール登録 → /member/profile). */
    static final String SESSION_MEMBER_ID = "memberUserId";

    private final SiteDesignService siteDesignService;
    private final PublicSiteService publicSiteService;
    private final MemberRegistrationService registrationService;
    private final MemberPageService memberPageService;
    private final CrmUserRepository userRepository;
    private final UserPointService userPointService;

    public PublicSiteController(SiteDesignService siteDesignService, PublicSiteService publicSiteService,
                                MemberRegistrationService registrationService, MemberPageService memberPageService,
                                CrmUserRepository userRepository, UserPointService userPointService) {
        this.siteDesignService = siteDesignService;
        this.publicSiteService = publicSiteService;
        this.registrationService = registrationService;
        this.memberPageService = memberPageService;
        this.userRepository = userRepository;
        this.userPointService = userPointService;
    }

    @GetMapping("/page/{code}")
    public String page(@PathVariable String code, Model model, HttpServletResponse response) {
        String title = SiteDesignService.PAGES.get(code);
        if (title == null) {
            // Same no-info 404 page as unknown URLs (GlobalExceptionHandler#handle404), which
            // would otherwise turn a thrown ResponseStatusException into a 500.
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return "error/404";
        }
        addCommon(model);
        model.addAttribute("pageTitle", title);
        model.addAttribute("pageHtml", siteDesignService.getPageHtml(code));
        return "member/page";
    }

    @PostMapping("/member/register")
    public String register(@RequestParam(required = false) String gender,
                           @RequestParam(required = false) String nickname,
                           @RequestParam(required = false) String email,
                           @RequestParam(required = false) String password,
                           @RequestParam(required = false) String agree,
                           HttpServletRequest request, Model model) {
        String confirmUrl = ServletUriComponentsBuilder.fromCurrentContextPath().path("/member/confirm").toUriString();
        try {
            registrationService.register(gender, nickname, email, password, agree != null,
                    ClientIpResolver.resolve(request), confirmUrl);
        } catch (MemberRegistrationService.RegistrationException e) {
            addCommon(model);
            model.addAttribute("pageTitle", "入力内容をご確認ください");
            model.addAttribute("pageHtml", errorsHtml(e.getErrors()));
            return "member/page";
        }
        return "redirect:/member/register/done";
    }

    @GetMapping("/member/register/done")
    public ResponseEntity<String> registerDone() {
        return html(publicSiteService.renderRegisterPage(PublicSiteService.PAGE_REGISTER_DONE, false));
    }

    @GetMapping("/member/confirm")
    public String confirm(@RequestParam(required = false) String token, HttpServletRequest request, Model model) {
        MemberRegistrationService.Confirmation c = registrationService.confirm(token);
        switch (c.result) {
            case CONFIRMED:
            case ALREADY_ACTIVE:
                request.getSession(true).setAttribute(SESSION_MEMBER_ID, c.user.getId());
                return "redirect:/member/register/complete";
            case EXPIRED:
                addCommon(model);
                model.addAttribute("pageTitle", "URLの有効期限が切れています");
                model.addAttribute("pageHtml", "<p>本登録のURLの有効期限（" + MemberRegistrationService.TOKEN_VALID_HOURS
                        + "時間）が切れています。お手数ですが、<a href=\"/#register\">もう一度登録</a>してください。</p>");
                return "member/page";
            default:
                addCommon(model);
                model.addAttribute("pageTitle", "URLが正しくありません");
                model.addAttribute("pageHtml", "<p>本登録のURLが正しくないか、すでに使われています。"
                        + "メールに記載されたURLをもう一度ご確認ください。</p>");
                return "member/page";
        }
    }

    @GetMapping("/member/register/complete")
    public ResponseEntity<String> registerComplete() {
        return html(publicSiteService.renderRegisterPage(PublicSiteService.PAGE_REGISTER_COMPLETE, false));
    }

    /** プロフ編集 for the member who completed 本登録 in this browser; others go to the login dialog. */
    @GetMapping(PublicSiteService.PROFILE_URL)
    public Object profile(HttpSession session) {
        Object id = session.getAttribute(SESSION_MEMBER_ID);
        Optional<CrmUser> user = id instanceof Long ? userRepository.findById((Long) id) : Optional.<CrmUser>empty();
        if (!user.isPresent() || !CrmUser.STATUS_ACTIVE.equals(user.get().getStatus())) {
            return "redirect:/#login";
        }
        CrmUser u = user.get();
        Map<String, String> values = new HashMap<>();
        values.put("id", u.getLoginId());
        values.put("name", u.getDisplayName());
        values.put("point", String.format("%,d", userPointService.get(u.getId())));
        values.put("email", u.getEmail());
        // only プロフ編集 is open to members so far; the design's other links stay on this page
        return html(memberPageService.renderForMember("profile", "sp", values, u.getFolder(),
                code -> "profile".equals(code) ? PublicSiteService.PROFILE_URL : "#"));
    }

    @PostMapping("/member/login")
    public String pending(Model model) {
        addCommon(model);
        model.addAttribute("pageTitle", "準備中");
        model.addAttribute("pageHtml", "<p>会員ログインは現在準備中です。公開まで今しばらくお待ちください。</p>");
        return "member/page";
    }

    private static String errorsHtml(List<String> errors) {
        StringBuilder b = new StringBuilder("<ul>");
        for (String e : errors) b.append("<li>").append(HtmlUtils.htmlEscape(e, "UTF-8")).append("</li>");
        return b.append("</ul><p><a href=\"/#register\">登録フォームに戻る</a></p>").toString();
    }

    private static ResponseEntity<String> html(String body) {
        return ResponseEntity.ok().header("Content-Type", "text/html; charset=UTF-8").body(body);
    }

    private void addCommon(Model model) {
        model.addAttribute("siteName", siteDesignService.getSiteName());
        model.addAttribute("logoUrl", siteDesignService.getLogoUrl());
        model.addAttribute("footerHtml", publicSiteService.footerHtml("/#login"));
    }
}
