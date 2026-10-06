package com.crm.service;

import com.crm.entity.HtmlImage;
import com.crm.entity.LineAccountPhoto;
import com.crm.repository.LineAccountPhotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * LINEアカウント編集 › 写真 — resized in the browser like キャラ登録 (long side 640px, ~200KB) and
 * stored in HTML画像管理; shown on the 返信画面's キャラ card.
 */
@Service
public class LineAccountPhotoService {

    public static class PhotoException extends RuntimeException {
        public PhotoException(String msg) { super(msg); }
    }

    private final LineAccountPhotoRepository repository;
    private final HtmlImageService htmlImageService;

    public LineAccountPhotoService(LineAccountPhotoRepository repository, HtmlImageService htmlImageService) {
        this.repository = repository;
        this.htmlImageService = htmlImageService;
    }

    /** The account's photo URL, or null. */
    public String photoUrl(Long lineAccountId) {
        if (lineAccountId == null) return null;
        return repository.findById(lineAccountId).map(LineAccountPhoto::getPhotoUrl).orElse(null);
    }

    @Transactional
    public String save(Long lineAccountId, String accountName, MultipartFile photo, String uploadedBy) {
        if (photo == null || photo.isEmpty()) throw new PhotoException("写真を選んでください");
        try {
            HtmlImage img = htmlImageService.upload(photo, "LINEキャラ写真 " + (accountName == null ? lineAccountId : accountName), uploadedBy);
            LineAccountPhoto p = repository.findById(lineAccountId).orElseGet(LineAccountPhoto::new);
            p.setLineAccountId(lineAccountId);
            p.setPhotoUrl("/img/" + img.getId());
            repository.save(p);
            return p.getPhotoUrl();
        } catch (HtmlImageService.HtmlImageException | IOException e) {
            throw new PhotoException("写真の保存に失敗しました: " + e.getMessage());
        }
    }

    @Transactional
    public void remove(Long lineAccountId) {
        if (lineAccountId != null && repository.existsById(lineAccountId)) repository.deleteById(lineAccountId);
    }
}
