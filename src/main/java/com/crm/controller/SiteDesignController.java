package com.crm.controller;

import com.crm.entity.HtmlImage;
import com.crm.interceptor.AuthInterceptor;
import com.crm.service.AuditLogService;
import com.crm.service.DomainSettingService;
import com.crm.service.HtmlImageService;
import com.crm.service.PublicSiteService;
import com.crm.service.SiteDesignService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * サイト構成 › 番組デザイン設定 — the public member site's replaceable logo / top images,
 * お問い合わせ address and footer pages (see {@link SiteDesignService}), plus a preview of
 * the pre-login top page that works even while a 本ドメイン表示設定 pattern is active.
 */
@Controller
@RequestMapping("/manager/settings/site-design")
public class SiteDesignController {

    private static final String PAGE_PARAM_PREFIX = "page_";
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    /** Preview frame width per device (px). ガラケー = a typical 240px feature-phone screen. */
    private static final Map<String, Integer> DEVICE_WIDTHS = new java.util.LinkedHashMap<>();
    private static final Map<String, String> DEVICE_LABELS = new java.util.LinkedHashMap<>();
    static {
        DEVICE_WIDTHS.put("pc", 1280); DEVICE_LABELS.put("pc", "PC");
        DEVICE_WIDTHS.put("sp", 390);  DEVICE_LABELS.put("sp", "スマホ");
        DEVICE_WIDTHS.put("fp", 240);  DEVICE_LABELS.put("fp", "ガラケー");
    }

    private final SiteDesignService siteDesignService;
    private final PublicSiteService publicSiteService;
    private final HtmlImageService htmlImageService;
    private final AuditLogService auditLog;
    private final com.crm.service.FolderSettingService folderSettingService;
    private final com.crm.service.MemberPageService memberPageService;

    public SiteDesignController(SiteDesignService siteDesignService, PublicSiteService publicSiteService,
                                HtmlImageService htmlImageService, AuditLogService auditLog,
                                com.crm.service.FolderSettingService folderSettingService,
                                com.crm.service.MemberPageService memberPageService) {
        this.siteDesignService = siteDesignService;
        this.publicSiteService = publicSiteService;
        this.htmlImageService = htmlImageService;
        this.auditLog = auditLog;
        this.folderSettingService = folderSettingService;
        this.memberPageService = memberPageService;
    }

    @GetMapping
    public String form(Model model) {
        model.addAttribute("pageTitles", SiteDesignService.PAGES);
        model.addAttribute("pages", siteDesignService.getPages());
        model.addAttribute("logoUrl", siteDesignService.getLogoUrl());
        model.addAttribute("topPcUrl", siteDesignService.getTopImagePcUrl());
        model.addAttribute("topSpUrl", siteDesignService.getTopImageSpUrl());
        model.addAttribute("contactEmail", siteDesignService.getContactEmailSetting());
        model.addAttribute("contactEmailDefault", siteDesignService.getContactEmail());
        String topHtml = siteDesignService.getTopHtml();
        model.addAttribute("topHtml", topHtml != null ? topHtml : publicSiteService.getDefaultTopTemplate());
        model.addAttribute("topHtmlCustomized", topHtml != null);
        model.addAttribute("footerNote", siteDesignService.getFooterNote());
        model.addAttribute("slots", siteDesignService.getSlots());
        model.addAttribute("maxSlotHtml", SiteDesignService.MAX_SLOT_HTML_CHARS);
        model.addAttribute("maxSlotCss", SiteDesignService.MAX_SLOT_CSS_CHARS);
        model.addAttribute("folders", folderSettingService.listFolders());
        return "setting/site-design";
    }

    @PostMapping
    public String save(@RequestParam Map<String, String> params,
                       @RequestParam(value = "logoFile", required = false) MultipartFile logoFile,
                       @RequestParam(value = "topPcFile", required = false) MultipartFile topPcFile,
                       @RequestParam(value = "topSpFile", required = false) MultipartFile topSpFile,
                       HttpServletRequest request, HttpSession session, RedirectAttributes ra) {
        List<String> errors = new ArrayList<>();

        Map<String, String> pages = new HashMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getKey().startsWith(PAGE_PARAM_PREFIX)) {
                pages.put(e.getKey().substring(PAGE_PARAM_PREFIX.length()), e.getValue());
            }
        }
        siteDesignService.savePages(pages);

        // Pre-login page HTML: saving the untouched default stores nothing, so the page keeps
        // following the bundled design; "初期デザインに戻す" clears any saved edit.
        String topHtml = params.get("topHtml");
        if ("true".equals(params.get("topHtmlReset"))) {
            siteDesignService.saveTopHtml(null);
        } else if (topHtml != null) {
            String normalized = topHtml.replace("\r\n", "\n");
            String def = publicSiteService.getDefaultTopTemplate().replace("\r\n", "\n");
            try {
                siteDesignService.saveTopHtml(normalized.trim().equals(def.trim()) ? null : normalized);
            } catch (IllegalArgumentException e) {
                errors.add("ログイン前ページ: " + e.getMessage());
            }
        }
        if (params.containsKey("footerNote")) siteDesignService.saveFooterNote(params.get("footerNote"));

        // ログイン後: each page's top / bottom HTML areas and CSS; the page layout itself is fixed.
        for (Map.Entry<String, String> p : SiteDesignService.MEMBER_PAGES.entrySet()) {
            String code = p.getKey();
            try {
                siteDesignService.saveSlot(code, params.get("slottop_" + code),
                        params.get("slotbottom_" + code), params.get("slotcss_" + code));
            } catch (IllegalArgumentException e) {
                errors.add(p.getValue() + ": " + e.getMessage());
            }
            // 全表示 / フォルダ限定, chosen to the right of each HTML area.
            siteDesignService.saveSlotFolders(code,
                    folderScope(request, "slottop", code, p.getValue() + " 上部HTML", errors),
                    folderScope(request, "slotbottom", code, p.getValue() + " 下部HTML", errors));
        }

        String contact = params.get("contactEmail");
        if (contact != null) {
            String c = contact.trim();
            if (c.isEmpty() || EMAIL.matcher(c).matches()) {
                siteDesignService.saveContactEmail(c);
            } else {
                errors.add("お問い合わせメールアドレスの形式が正しくありません");
            }
        }

        Object admin = session.getAttribute(AuthInterceptor.SESSION_ADMIN_NAME);
        String uploadedBy = admin == null ? null : String.valueOf(admin);
        applyImage(DomainSettingService.KEY_SITE_LOGO_URL, "サイトロゴ", logoFile,
                "true".equals(params.get("logoRemove")), uploadedBy, errors);
        applyImage(SiteDesignService.KEY_TOP_IMAGE_PC, "トップ画像（PC）", topPcFile,
                "true".equals(params.get("topPcRemove")), uploadedBy, errors);
        applyImage(SiteDesignService.KEY_TOP_IMAGE_SP, "トップ画像（スマホ）", topSpFile,
                "true".equals(params.get("topSpRemove")), uploadedBy, errors);

        auditLog.record(AuditLogService.ACTION_SETTINGS_UPDATE, "SiteDesign", null, "番組デザイン設定を更新");
        if (errors.isEmpty()) {
            ra.addFlashAttribute("flashSuccess", "番組デザイン設定を保存しました");
        } else {
            ra.addFlashAttribute("flashError", String.join(" / ", errors) + "（その他の項目は保存しました）");
        }
        // Saved from a ログイン後 section's own button → come back to that section.
        String jump = params.get("jump");
        if (jump != null && SiteDesignService.MEMBER_PAGES.containsKey(jump)) {
            return "redirect:/manager/settings/site-design#slot-" + jump;
        }
        return "redirect:/manager/settings/site-design";
    }

    /** PC / スマホ / ガラケー check: the pre-login page in a frame of that device's width. */
    @GetMapping("/preview-frame")
    public String previewFrame(@RequestParam(value = "device", defaultValue = "pc") String device, Model model) {
        if (!DEVICE_WIDTHS.containsKey(device)) device = "pc";
        model.addAttribute("device", device);
        model.addAttribute("deviceWidths", DEVICE_WIDTHS);
        model.addAttribute("deviceLabels", DEVICE_LABELS);
        return "setting/site-design-preview";
    }

    /** The pre-login top page as visitors would see it with the saved settings;
     *  ガラケー (device=fp) shows its own design. */
    @GetMapping("/preview")
    public ResponseEntity<String> preview(@RequestParam(value = "device", defaultValue = "pc") String device,
                                          HttpServletRequest request) {
        Object csrf = request.getAttribute("_csrf");
        String token = csrf == null ? null : csrf.toString();
        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body("fp".equals(device) ? publicSiteService.renderTopFp(token) : publicSiteService.renderTop(token));
    }

    /**
     * ログイン後 check: one post-login page (approved design) in a frame of the device's width,
     * with page tabs and an on/off switch for outlining the 上部 / 下部 HTML areas.
     */
    @GetMapping("/member-preview-frame")
    public String memberPreviewFrame(@RequestParam(value = "device", defaultValue = "sp") String device,
                                     @RequestParam(value = "page", defaultValue = "menu") String page,
                                     @RequestParam(value = "mark", defaultValue = "1") String mark,
                                     Model model) {
        if (!DEVICE_WIDTHS.containsKey(device)) device = "sp";
        if (!com.crm.service.MemberPageService.BAR_TITLES.containsKey(page)) page = "menu";
        model.addAttribute("device", device);
        model.addAttribute("page", page);
        model.addAttribute("mark", "1".equals(mark));
        model.addAttribute("deviceWidths", DEVICE_WIDTHS);
        model.addAttribute("deviceLabels", DEVICE_LABELS);
        model.addAttribute("memberPages", SiteDesignService.MEMBER_PAGES);
        model.addAttribute("fpPages", com.crm.service.MemberPageService.FP_PAGES);
        return "setting/site-design-member-preview";
    }

    /** One post-login page with the saved HTML areas / CSS and sample member values. */
    @GetMapping("/member-preview")
    public ResponseEntity<String> memberPreview(@RequestParam(value = "device", defaultValue = "sp") String device,
                                                @RequestParam(value = "page", defaultValue = "menu") String page,
                                                @RequestParam(value = "mark", defaultValue = "1") String mark) {
        String dev = DEVICE_WIDTHS.containsKey(device) ? device : "sp";
        String m = "1".equals(mark) ? "1" : "0";
        String html = memberPageService.renderPreview(page, dev, "1".equals(m),
                code -> "member-preview?page=" + code + "&device=" + dev + "&mark=" + m);
        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(html);
    }

    /** One HTML area's submitted folder scope: empty list = 全表示, else the checked folders;
     *  null (= keep the saved setting) when the area wasn't submitted, or フォルダ限定 was chosen
     *  with no folder checked — that would hide the area from every member. */
    private static List<String> folderScope(HttpServletRequest request, String prefix, String code,
                                            String label, List<String> errors) {
        String scope = request.getParameter(prefix + "scope_" + code);
        if (scope == null) return null;
        if (!"folders".equals(scope)) return java.util.Collections.emptyList();
        String[] checked = request.getParameterValues(prefix + "folders_" + code);
        if (checked == null || checked.length == 0) {
            errors.add(label + ": フォルダ限定の場合は表示するフォルダを1つ以上選んでください");
            return null;
        }
        return java.util.Arrays.asList(checked);
    }

    /** A chosen file replaces the image (stored in HTML画像管理); the remove box clears it back
     *  to the default design; otherwise the current image is kept. */
    private void applyImage(String key, String label, MultipartFile file, boolean remove,
                            String uploadedBy, List<String> errors) {
        if (file != null && !file.isEmpty()) {
            try {
                HtmlImage img = htmlImageService.upload(file, label, uploadedBy);
                siteDesignService.saveImageUrl(key, "/img/" + img.getId());
            } catch (HtmlImageService.HtmlImageException | IOException e) {
                errors.add(label + "の登録に失敗しました: " + e.getMessage());
            }
        } else if (remove) {
            siteDesignService.saveImageUrl(key, null);
        }
    }
}
