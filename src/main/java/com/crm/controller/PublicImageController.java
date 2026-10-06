package com.crm.controller;

import com.crm.entity.HtmlImage;
import com.crm.service.HtmlImageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.File;
import java.util.concurrent.TimeUnit;

/** Public, unauthenticated image serving — deliberately NOT under /manager/** (session-gated)
 *  or /media/** (Basic-Auth-gated), so an <img src="/img/{id}"> pasted into reply-page header
 *  or footer HTML renders correctly for anonymous visitors on the public /reply/{token} page. */
@Controller
public class PublicImageController {

    private final HtmlImageService htmlImageService;

    public PublicImageController(HtmlImageService htmlImageService) {
        this.htmlImageService = htmlImageService;
    }

    /** 画像添付 of メール / SMS — shown to members only through the 返信画面 / 会員ページ (写真閲覧 points). */
    private com.crm.repository.MessageImageRepository messageImageRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setMessageImageRepository(com.crm.repository.MessageImageRepository messageImageRepository) {
        this.messageImageRepository = messageImageRepository;
    }

    @GetMapping("/img/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> serve(@PathVariable Long id, javax.servlet.http.HttpServletRequest request) {
        HtmlImage img = htmlImageService.findById(id).orElse(null);
        if (img == null) return ResponseEntity.notFound().build();
        File f = htmlImageService.fileFor(img);
        if (f == null) return ResponseEntity.notFound().build();
        // A メール / SMS 画像添付 is not public: members open it with 写真閲覧 points on the 返信画面 /
        // 会員ページ (their own URLs); here only the 管理画面 (logged-in admin) sees it. LINE画像挿入
        // stays public — LINE fetches it from this URL.
        if (img.getLabel() != null && img.getLabel().startsWith(HtmlImageService.LABEL_ATTACH)
                && messageImageRepository != null && messageImageRepository.existsByImageId(id)) {
            javax.servlet.http.HttpSession s = request.getSession(false);
            if (s == null || s.getAttribute(com.crm.interceptor.AuthInterceptor.SESSION_ADMIN_ID) == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(img.getContentType()))
                    .cacheControl(CacheControl.noStore()).body(new FileSystemResource(f));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.getContentType()))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(new FileSystemResource(f));
    }
}
