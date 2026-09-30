package com.crm.controller;

import com.crm.service.PublicSiteService;
import com.crm.service.SiteDesignService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import javax.servlet.http.HttpServletResponse;


/**
 * Public member (番組) site pages outside the top page: the footer pages edited on
 * 番組デザイン設定 (/page/{code}), and the register / login form targets.
 *
 * Member accounts (registration, confirmation mail, login) are not built yet, so the two form
 * posts answer with a 準備中 page rather than the design's demo "確認メールを送りました".
 */
@Controller
public class PublicSiteController {

    private final SiteDesignService siteDesignService;
    private final PublicSiteService publicSiteService;

    public PublicSiteController(SiteDesignService siteDesignService, PublicSiteService publicSiteService) {
        this.siteDesignService = siteDesignService;
        this.publicSiteService = publicSiteService;
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

    @PostMapping({"/member/register", "/member/login"})
    public String pending(Model model) {
        addCommon(model);
        model.addAttribute("pageTitle", "準備中");
        model.addAttribute("pageHtml", "<p>会員登録・ログインは現在準備中です。公開まで今しばらくお待ちください。</p>");
        return "member/page";
    }

    private void addCommon(Model model) {
        model.addAttribute("siteName", siteDesignService.getSiteName());
        model.addAttribute("logoUrl", siteDesignService.getLogoUrl());
        model.addAttribute("footerHtml", publicSiteService.footerHtml("/#login"));
    }
}
