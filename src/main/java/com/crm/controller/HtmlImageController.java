package com.crm.controller;

import com.crm.entity.HtmlImage;
import com.crm.interceptor.AuthInterceptor;
import com.crm.service.AuditLogService;
import com.crm.service.DomainSettingService;
import com.crm.service.HtmlImageService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Admin management UI for operator-uploaded images (upload / list / edit label / delete).
 *  Actual public image serving lives in {@link PublicImageController} at /img/{id}, outside
 *  /manager/** so it renders unauthenticated on the public reply page. */
@Controller
@RequestMapping("/manager/settings/html-images")
public class HtmlImageController {

    private final HtmlImageService htmlImageService;
    private final AuditLogService auditLog;
    private final DomainSettingService domainSettingService;

    public HtmlImageController(HtmlImageService htmlImageService, AuditLogService auditLog,
                                DomainSettingService domainSettingService) {
        this.htmlImageService = htmlImageService;
        this.auditLog = auditLog;
        this.domainSettingService = domainSettingService;
    }

    @ModelAttribute("adminName")
    public String adminName(HttpSession session) {
        Object name = session.getAttribute(AuthInterceptor.SESSION_ADMIN_NAME);
        return name == null ? null : String.valueOf(name);
    }

    @GetMapping({"", "/"})
    public String list(Model model) {
        model.addAttribute("images", htmlImageService.listAll());
        model.addAttribute("imageUrlBase", domainSettingService.getReplyBaseUrl());
        return "setting/html-images";
    }

    @PostMapping
    public String upload(@RequestParam("file") MultipartFile file,
                          @RequestParam(required = false) String label,
                          HttpSession session, RedirectAttributes ra) {
        Object name = session.getAttribute(AuthInterceptor.SESSION_ADMIN_NAME);
        try {
            HtmlImage img = htmlImageService.upload(file, label, name == null ? null : String.valueOf(name));
            auditLog.record(AuditLogService.ACTION_HTML_IMAGE_UPLOAD, "HtmlImage", img.getId(),
                    "file=" + img.getFileName() + " size=" + img.getSizeBytes());
            ra.addFlashAttribute("flashSuccess", "画像をアップロードしました");
        } catch (HtmlImageService.HtmlImageException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        } catch (IOException e) {
            ra.addFlashAttribute("flashError", "アップロードに失敗しました。再度お試しください。");
        }
        return "redirect:/manager/settings/html-images";
    }

    /** 画像添付 on the thread page's compose form: same upload as above, but answers JSON
     *  with the public /img/{id} URL so the page can insert it into 本文 without a reload. */
    @PostMapping("/compose-upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> composeUpload(@RequestParam("file") MultipartFile file,
                                                             HttpSession session) {
        Object name = session.getAttribute(AuthInterceptor.SESSION_ADMIN_NAME);
        Map<String, Object> out = new HashMap<>();
        try {
            HtmlImage img = htmlImageService.upload(file, "画像添付", name == null ? null : String.valueOf(name));
            auditLog.record(AuditLogService.ACTION_HTML_IMAGE_UPLOAD, "HtmlImage", img.getId(),
                    "file=" + img.getFileName() + " size=" + img.getSizeBytes() + " via=compose");
            out.put("id", img.getId());
            out.put("url", domainSettingService.getReplyBaseUrl() + "/img/" + img.getId());
            return ResponseEntity.ok(out);
        } catch (HtmlImageService.HtmlImageException e) {
            out.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(out);
        } catch (IOException e) {
            out.put("error", "アップロードに失敗しました。再度お試しください。");
            return ResponseEntity.status(500).body(out);
        }
    }

    @PostMapping("/{id}/label")
    public String updateLabel(@PathVariable Long id, @RequestParam String label, RedirectAttributes ra) {
        boolean ok = htmlImageService.updateLabel(id, label);
        ra.addFlashAttribute(ok ? "flashSuccess" : "flashError", ok ? "ラベルを更新しました" : "画像が見つかりません");
        return "redirect:/manager/settings/html-images";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        boolean ok = htmlImageService.deleteById(id);
        if (ok) {
            auditLog.record(AuditLogService.ACTION_HTML_IMAGE_DELETE, "HtmlImage", id, "deleted");
            ra.addFlashAttribute("flashSuccess", "画像を削除しました");
        } else {
            ra.addFlashAttribute("flashError", "画像が見つかりません");
        }
        return "redirect:/manager/settings/html-images";
    }
}
