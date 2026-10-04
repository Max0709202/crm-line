package com.crm.service;

import com.crm.entity.Chara;
import com.crm.entity.CharaFolder;
import com.crm.entity.HtmlImage;
import com.crm.repository.CharaFolderRepository;
import com.crm.repository.CharaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 会員管理 › キャラ登録 — operator characters (男性 / 女性) in folders. Photos are resized in the
 * browser (long side 640px, ~200KB) and stored in HTML画像管理 like the other site images.
 */
@Service
public class CharaService {

    public static final List<String> PREFS = Collections.unmodifiableList(Arrays.asList(
            "北海道", "青森県", "岩手県", "宮城県", "秋田県", "山形県", "福島県", "茨城県", "栃木県", "群馬県", "埼玉県", "千葉県",
            "東京都", "神奈川県", "新潟県", "富山県", "石川県", "福井県", "山梨県", "長野県", "岐阜県", "静岡県", "愛知県", "三重県",
            "滋賀県", "京都府", "大阪府", "兵庫県", "奈良県", "和歌山県", "鳥取県", "島根県", "岡山県", "広島県", "山口県", "徳島県",
            "香川県", "愛媛県", "高知県", "福岡県", "佐賀県", "長崎県", "熊本県", "大分県", "宮崎県", "鹿児島県", "沖縄県"));
    public static final List<String> BLOODS = Collections.unmodifiableList(Arrays.asList("A型", "B型", "O型", "AB型"));
    public static final List<String> SIGNS = Collections.unmodifiableList(Arrays.asList(
            "おひつじ座", "おうし座", "ふたご座", "かに座", "しし座", "おとめ座", "てんびん座", "さそり座", "いて座", "やぎ座", "みずがめ座", "うお座"));
    public static final int MIN_AGE = 18;
    public static final int MAX_AGE = 80;

    /** What the キャラ登録 form submits. */
    public static final class Input {
        public String name;
        public String gender;
        public String pref;
        public String blood;
        public String sign;
        public String age;
        public String profile;
        public String folderId;
        /** a new photo, or null to keep the current one */
        public MultipartFile photo;
        public boolean removePhoto;
    }

    public static class CharaException extends RuntimeException {
        public CharaException(String msg) { super(msg); }
    }

    private final CharaRepository repository;
    private final CharaFolderRepository folderRepository;
    private final HtmlImageService htmlImageService;

    public CharaService(CharaRepository repository, CharaFolderRepository folderRepository,
                        HtmlImageService htmlImageService) {
        this.repository = repository;
        this.folderRepository = folderRepository;
        this.htmlImageService = htmlImageService;
    }

    public List<Chara> list() { return repository.findAllByOrderByIdAsc(); }

    public List<CharaFolder> folders() { return folderRepository.findAllByOrderByIdAsc(); }

    @Transactional
    public Chara save(Long id, Input in, String uploadedBy) {
        Chara c = id == null ? new Chara()
                : repository.findById(id).orElseThrow(() -> new CharaException("キャラが見つかりません"));
        String name = trim(in.name);
        if (name.isEmpty()) throw new CharaException("表示名を入力してください");
        if (name.length() > 20) throw new CharaException("表示名は20文字までです");
        if (!Chara.GENDER_MALE.equals(in.gender) && !Chara.GENDER_FEMALE.equals(in.gender)) {
            throw new CharaException("性別を選んでください");
        }
        String profile = trim(in.profile);
        if (profile.length() > 500) throw new CharaException("プロフィールは500文字までです");
        Integer age = null;
        if (!trim(in.age).isEmpty()) {
            try { age = Integer.valueOf(trim(in.age)); } catch (NumberFormatException e) { age = -1; }
            if (age < MIN_AGE || age > MAX_AGE) throw new CharaException("年齢が正しくありません");
        }
        Long folderId = null;
        if (!trim(in.folderId).isEmpty()) {
            try { folderId = Long.valueOf(trim(in.folderId)); } catch (NumberFormatException e) { folderId = -1L; }
            if (!folderRepository.existsById(folderId)) throw new CharaException("フォルダが見つかりません");
        }
        c.setName(name);
        c.setGender(in.gender);
        c.setPref(oneOf(in.pref, PREFS));
        c.setBlood(oneOf(in.blood, BLOODS));
        c.setSign(oneOf(in.sign, SIGNS));
        c.setAge(age);
        c.setProfile(profile.isEmpty() ? null : profile);
        c.setFolderId(folderId);
        if (in.photo != null && !in.photo.isEmpty()) {
            try {
                HtmlImage img = htmlImageService.upload(in.photo, "キャラ写真 " + name, uploadedBy);
                c.setPhotoUrl("/img/" + img.getId());
            } catch (HtmlImageService.HtmlImageException | IOException e) {
                throw new CharaException("写真の保存に失敗しました: " + e.getMessage());
            }
        } else if (in.removePhoto) {
            c.setPhotoUrl(null);
        }
        return repository.save(c);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new CharaException("キャラが見つかりません");
        repository.deleteById(id);
    }

    @Transactional
    public CharaFolder saveFolder(Long id, String rawName) {
        String name = trim(rawName);
        if (name.isEmpty()) throw new CharaException("フォルダ名を入力してください");
        if (name.length() > 20) throw new CharaException("フォルダ名は20文字までです");
        CharaFolder f = id == null ? new CharaFolder()
                : folderRepository.findById(id).orElseThrow(() -> new CharaException("フォルダが見つかりません"));
        f.setName(name);
        return folderRepository.save(f);
    }

    /** Deletes a folder; its キャラ move to 未分類. */
    @Transactional
    public void deleteFolder(Long id) {
        if (!folderRepository.existsById(id)) throw new CharaException("フォルダが見つかりません");
        repository.clearFolder(id);
        folderRepository.deleteById(id);
    }

    private static String oneOf(String v, List<String> allowed) {
        return v != null && allowed.contains(v) ? v : null;
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }
}
