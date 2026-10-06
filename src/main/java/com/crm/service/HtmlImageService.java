package com.crm.service;

import com.crm.entity.HtmlImage;
import com.crm.repository.HtmlImageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Manages operator-uploaded images referenced from CRM HTML fields (reply-page header/footer
 * HTML, memo-slot HTML, etc.) and served publicly at /img/{id}.
 *
 * <p>Storage layout: files live flat under {@link #uploadsRoot}/{uuid}.{ext} — no per-user
 * directory since images here are shared/global, unlike {@link ReplyAttachmentService}'s
 * per-user reply attachments.
 */
@Service
public class HtmlImageService {

    private static final Logger log = LoggerFactory.getLogger(HtmlImageService.class);

    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB
    public static final Set<String> ALLOWED_MIME = new java.util.HashSet<>(Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp"));

    private final HtmlImageRepository repo;
    private final Path uploadsRoot;

    public HtmlImageService(HtmlImageRepository repo,
                             @Value("${app.html-images-uploads-root:/home/centos/crm-platform/uploads/html-images}")
                             String uploadsRoot) {
        this.repo = repo;
        this.uploadsRoot = Paths.get(uploadsRoot);
        try {
            Files.createDirectories(this.uploadsRoot);
        } catch (IOException e) {
            log.warn("Failed to create uploads root {}: {}", uploadsRoot, e.toString());
        }
    }

    /**
     * HTML画像管理's columns: category key → name, in display order. An image's category comes from
     * the label its upload screen gives it (see {@link #categoryOf}); ユーザーからの画像添付 (the
     * reply page's uploads, ReplyAttachmentService) is shown as {@link #CATEGORY_USER_ATTACH}.
     */
    public static final Map<String, String> CATEGORIES;
    public static final String CATEGORY_USER_ATTACH = "userAttach";
    public static final String CATEGORY_OTHER = "other";
    /** Label of an image attached on 返信 / 一斉送信 / 差分 (shown on the 返信画面, not in the mail). */
    public static final String LABEL_ATTACH = "画像添付";
    /** Label of an image inserted into a LINE message (sent to LINE as an image). */
    public static final String LABEL_LINE_INSERT = "LINE画像挿入";
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("chara", "キャラ写真");
        m.put("user", "ユーザー写真");
        m.put("attach", "画像添付");
        m.put("lineInsert", "LINE画像挿入");
        m.put(CATEGORY_USER_ATTACH, "ユーザーからの画像添付");
        m.put("lineChara", "LINEキャラ写真");
        m.put("persona", "line-persona");
        m.put("site", "サイトロゴ・トップ画像");
        m.put(CATEGORY_OTHER, "その他（このページでアップロード）");
        CATEGORIES = Collections.unmodifiableMap(m);
    }

    /** Category of an HTML画像 from its label (the label each upload screen sets). */
    public static String categoryOf(HtmlImage img) {
        String l = img.getLabel() == null ? "" : img.getLabel();
        if (l.startsWith("キャラ写真")) return "chara";
        if (l.startsWith("ユーザー写真")) return "user";
        if (l.startsWith(LABEL_LINE_INSERT)) return "lineInsert";
        if (l.startsWith(LABEL_ATTACH)) return "attach";
        if (l.startsWith("LINEキャラ写真")) return "lineChara";
        if (l.startsWith("line-persona")) return "persona";
        if (l.startsWith("サイトロゴ") || l.startsWith("トップ画像")) return "site";
        return CATEGORY_OTHER;
    }

    public List<HtmlImage> listAll() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    public Optional<HtmlImage> findById(Long id) {
        return repo.findById(id);
    }

    public File fileFor(HtmlImage img) {
        if (img == null || img.getStoredPath() == null) return null;
        File f = uploadsRoot.resolve(img.getStoredPath()).toFile();
        return f.isFile() ? f : null;
    }

    @Transactional
    public HtmlImage upload(MultipartFile file, String label, String uploadedBy)
            throws HtmlImageException, IOException {
        if (file == null || file.isEmpty()) throw new HtmlImageException("ファイルが空です");
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new HtmlImageException("ファイルサイズは " + (MAX_SIZE_BYTES / 1024 / 1024) + " MB 以内にしてください");
        }
        String mime = file.getContentType();
        if (mime == null || !ALLOWED_MIME.contains(mime.toLowerCase(Locale.ROOT))) {
            throw new HtmlImageException("画像ファイル (JPEG / PNG / GIF / WebP) のみアップロード可能です");
        }

        String ext = pickExtension(file.getOriginalFilename(), mime);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String rel = uuid + "." + ext;
        Path dest = uploadsRoot.resolve(rel);
        try (java.io.InputStream in = file.getInputStream()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }

        HtmlImage img = new HtmlImage();
        img.setLabel(label == null || label.trim().isEmpty() ? null : label.trim());
        img.setFileName(sanitiseFilename(file.getOriginalFilename(), ext));
        img.setStoredPath(rel);
        img.setContentType(mime.toLowerCase(Locale.ROOT));
        img.setSizeBytes(file.getSize());
        img.setUploadedBy(uploadedBy);
        return repo.save(img);
    }

    @Transactional
    public boolean updateLabel(Long id, String label) {
        return repo.findById(id).map(img -> {
            img.setLabel(label == null || label.trim().isEmpty() ? null : label.trim());
            repo.save(img);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean deleteById(Long id) {
        return repo.findById(id).map(img -> {
            File f = uploadsRoot.resolve(img.getStoredPath()).toFile();
            if (f.isFile()) {
                if (!f.delete()) log.warn("Could not delete image file {}", f.getAbsolutePath());
            }
            repo.delete(img);
            return true;
        }).orElse(false);
    }

    private static String pickExtension(String originalName, String mime) {
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot > 0 && dot < originalName.length() - 1) {
                String ext = originalName.substring(dot + 1).toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]", "");
                if (ext.length() >= 2 && ext.length() <= 5) return ext;
            }
        }
        switch (mime == null ? "" : mime.toLowerCase(Locale.ROOT)) {
            case "image/png":  return "png";
            case "image/gif":  return "gif";
            case "image/webp": return "webp";
            default:           return "jpg";
        }
    }

    private static String sanitiseFilename(String original, String ext) {
        if (original == null || original.isEmpty()) return "image." + ext;
        String name = original.replaceAll(".*[\\\\/]", "");
        if (name.length() > 240) name = name.substring(0, 240);
        return name;
    }

    public static class HtmlImageException extends RuntimeException {
        public HtmlImageException(String msg) { super(msg); }
    }
}
