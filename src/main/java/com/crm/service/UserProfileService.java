package com.crm.service;

import com.crm.entity.HtmlImage;
import com.crm.entity.UserProfile;
import com.crm.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * ユーザー詳細 › プロフィール: 都道府県 / 血液型 / 星座 / 年齢 / プロフィール欄 / 写真. Choices and limits
 * are the キャラ登録 ones ({@link CharaService}); the photo is resized in the browser the same way
 * (long side 640px, ~200KB) and stored in HTML画像管理.
 */
@Service
public class UserProfileService {

    public static class ProfileException extends RuntimeException {
        public ProfileException(String msg) { super(msg); }
    }

    private final UserProfileRepository repository;
    private final HtmlImageService htmlImageService;

    public UserProfileService(UserProfileRepository repository, HtmlImageService htmlImageService) {
        this.repository = repository;
        this.htmlImageService = htmlImageService;
    }

    /** The member's profile; an empty (unsaved) one when nothing is set yet. */
    public UserProfile get(Long userId) {
        return repository.findById(userId).orElseGet(() -> {
            UserProfile p = new UserProfile();
            p.setUserId(userId);
            return p;
        });
    }

    @Transactional
    public UserProfile save(Long userId, String pref, String blood, String sign, String age, String profile) {
        String text = trim(profile);
        if (text.length() > 500) throw new ProfileException("プロフィールは500文字までです");
        Integer ageValue = null;
        if (!trim(age).isEmpty()) {
            try { ageValue = Integer.valueOf(trim(age)); } catch (NumberFormatException e) { ageValue = -1; }
            if (ageValue < CharaService.MIN_AGE || ageValue > CharaService.MAX_AGE) {
                throw new ProfileException("年齢は" + CharaService.MIN_AGE + "〜" + CharaService.MAX_AGE + "で入力してください");
            }
        }
        UserProfile p = get(userId);
        p.setPref(oneOf(pref, CharaService.PREFS));
        p.setBlood(oneOf(blood, CharaService.BLOODS));
        p.setSign(oneOf(sign, CharaService.SIGNS));
        p.setAge(ageValue);
        p.setProfile(text.isEmpty() ? null : text);
        return repository.save(p);
    }

    /** Stores a new photo (already resized in the browser); returns its URL. */
    @Transactional
    public String savePhoto(Long userId, MultipartFile photo, String uploadedBy) {
        if (photo == null || photo.isEmpty()) throw new ProfileException("写真を選んでください");
        try {
            HtmlImage img = htmlImageService.upload(photo, "ユーザー写真 ID" + userId, uploadedBy);
            UserProfile p = get(userId);
            p.setPhotoUrl("/img/" + img.getId());
            repository.save(p);
            return p.getPhotoUrl();
        } catch (HtmlImageService.HtmlImageException | IOException e) {
            throw new ProfileException("写真の保存に失敗しました: " + e.getMessage());
        }
    }

    @Transactional
    public void removePhoto(Long userId) {
        repository.findById(userId).ifPresent(p -> {
            p.setPhotoUrl(null);
            repository.save(p);
        });
    }

    private static String oneOf(String v, List<String> allowed) {
        return v != null && allowed.contains(v) ? v : null;
    }

    private static String trim(String v) { return v == null ? "" : v.trim(); }
}
