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
import java.util.List;
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
    private final com.crm.repository.ReplyPageAttachmentRepository attachmentRepository;
    private final com.crm.service.ReplyAttachmentService replyAttachmentService;

    public HtmlImageController(HtmlImageService htmlImageService, AuditLogService auditLog,
                                DomainSettingService domainSettingService,
                                com.crm.repository.ReplyPageAttachmentRepository attachmentRepository,
                                com.crm.service.ReplyAttachmentService replyAttachmentService) {
        this.htmlImageService = htmlImageService;
        this.auditLog = auditLog;
        this.domainSettingService = domainSettingService;
        this.attachmentRepository = attachmentRepository;
        this.replyAttachmentService = replyAttachmentService;
    }

    @ModelAttribute("adminName")
    public String adminName(HttpSession session) {
        Object name = session.getAttribute(AuthInterceptor.SESSION_ADMIN_NAME);
        return name == null ? null : String.valueOf(name);
    }

    /**
     * The images by kind, side by side (one column per category, newest first going down):
     * キャラ写真 / ユーザー写真 / 画像添付 / LINE画像挿入 / ユーザーからの画像添付 / LINEキャラ写真 /
     * line-persona / サイトロゴ / その他. Columns with no image are left out.
     */
    @GetMapping({"", "/"})
    public String list(Model model) {
        Map<String, List<Map<String, Object>>> byCategory = new java.util.LinkedHashMap<>();
        for (String key : HtmlImageService.CATEGORIES.keySet()) byCategory.put(key, new java.util.ArrayList<>());
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (HtmlImage img : htmlImageService.listAll()) {
            Map<String, Object> m = new HashMap<>();
            m.put("kind", "html");
            m.put("id", img.getId());
            m.put("src", "/img/" + img.getId());
            m.put("label", img.getLabel());
            m.put("fileName", img.getFileName());
            m.put("createdAt", img.getCreatedAt() == null ? "" : img.getCreatedAt().format(f));
            m.put("sizeKb", img.getSizeBytes() == null ? 0 : img.getSizeBytes() / 1024);
            byCategory.get(HtmlImageService.categoryOf(img)).add(m);
        }
        for (com.crm.entity.ReplyPageAttachment a : attachmentRepository.findAll(
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))) {
            Map<String, Object> m = new HashMap<>();
            m.put("kind", "att");
            m.put("id", a.getId());
            m.put("src", "/manager/users/" + a.getUserId() + "/attachment/" + a.getId());
            m.put("label", "ユーザーID " + a.getUserId());
            m.put("userId", a.getUserId());
            m.put("fileName", a.getFileName());
            m.put("createdAt", a.getCreatedAt() == null ? "" : a.getCreatedAt().format(f));
            m.put("sizeKb", a.getSizeBytes() == null ? 0 : a.getSizeBytes() / 1024);
            byCategory.get(HtmlImageService.CATEGORY_USER_ATTACH).add(m);
        }
        List<Map<String, Object>> columns = new java.util.ArrayList<>();
        int total = 0;
        for (Map.Entry<String, List<Map<String, Object>>> e : byCategory.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            Map<String, Object> col = new HashMap<>();
            col.put("key", e.getKey());
            col.put("name", HtmlImageService.CATEGORIES.get(e.getKey()));
            col.put("items", e.getValue());
            columns.add(col);
            total += e.getValue().size();
        }
        model.addAttribute("columns", columns);
        model.addAttribute("total", total);
        model.addAttribute("imageUrlBase", domainSettingService.getReplyBaseUrl());
        return "setting/html-images";
    }

    /** 選択削除: HTML画像 ({@code imageIds}) and ユーザーからの画像添付 ({@code attIds}). */
    @PostMapping("/bulk-delete")
    public String bulkDelete(@RequestParam(name = "imageIds", required = false) List<Long> imageIds,
                             @RequestParam(name = "attIds", required = false) List<Long> attIds,
                             RedirectAttributes ra) {
        int n = 0;
        if (imageIds != null) {
            for (Long id : imageIds) {
                if (id != null && htmlImageService.deleteById(id)) {
                    auditLog.record(AuditLogService.ACTION_HTML_IMAGE_DELETE, "HtmlImage", id, "deleted (選択削除)");
                    n++;
                }
            }
        }
        if (attIds != null) {
            for (Long id : attIds) {
                if (id != null && replyAttachmentService.deleteById(id)) {
                    auditLog.record(AuditLogService.ACTION_HTML_IMAGE_DELETE, "ReplyPageAttachment", id, "deleted (選択削除)");
                    n++;
                }
            }
        }
        if (n == 0) ra.addFlashAttribute("flashError", "削除する画像を選択してください");
        else ra.addFlashAttribute("flashSuccess", n + "件の画像を削除しました");
        return "redirect:/manager/settings/html-images";
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

    /** 画像添付 (メール / SMS) / 画像挿入 ({@code kind=line}: LINE, JPEG / PNG only) from the PC on the
     *  返信画面 / 一斉送信 / 差分ステップ: same upload as above, answering JSON with the image's id and
     *  /img/{id} URL so the page can add it to the message without a reload. */
    @PostMapping("/compose-upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> composeUpload(@RequestParam("file") MultipartFile file,
                                                             @RequestParam(name = "kind", required = false) String kind,
                                                             HttpSession session) {
        Object name = session.getAttribute(AuthInterceptor.SESSION_ADMIN_NAME);
        Map<String, Object> out = new HashMap<>();
        boolean line = "line".equals(kind);
        if (line && file != null && !"image/jpeg".equalsIgnoreCase(file.getContentType()) && !"image/png".equalsIgnoreCase(file.getContentType())) {
            out.put("error", "LINEに挿入できる画像はJPEG・PNGのみです");
            return ResponseEntity.badRequest().body(out);
        }
        try {
            HtmlImage img = htmlImageService.upload(file, line ? HtmlImageService.LABEL_LINE_INSERT : HtmlImageService.LABEL_ATTACH,
                    name == null ? null : String.valueOf(name));
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
