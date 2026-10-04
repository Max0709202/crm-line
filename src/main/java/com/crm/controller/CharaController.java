package com.crm.controller;

import com.crm.entity.Chara;
import com.crm.entity.CharaFolder;
import com.crm.interceptor.AuthInterceptor;
import com.crm.service.AuditLogService;
import com.crm.service.CharaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpSession;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 会員管理 › キャラ登録 — the client-approved screen (folders, ♂ / ♀ lists, register / edit form). */
@Controller
@RequestMapping("/manager/characters")
public class CharaController {

    private static final DateTimeFormatter ISO_MIN = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final CharaService service;
    private final AuditLogService auditLog;
    private final ObjectMapper objectMapper;

    public CharaController(CharaService service, AuditLogService auditLog, ObjectMapper objectMapper) {
        this.service = service;
        this.auditLog = auditLog;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String page(Model model) throws JsonProcessingException {
        List<Map<String, Object>> folders = new ArrayList<>();
        for (CharaFolder f : service.folders()) folders.add(folderJson(f));
        List<Map<String, Object>> charas = new ArrayList<>();
        for (Chara c : service.list()) charas.add(charaJson(c));
        model.addAttribute("foldersJson", json(folders));
        model.addAttribute("charasJson", json(charas));
        return "chara/register";
    }

    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> save(@RequestParam(required = false) String id,
                                                    @RequestParam(required = false) String name,
                                                    @RequestParam(required = false) String gender,
                                                    @RequestParam(required = false) String pref,
                                                    @RequestParam(required = false) String blood,
                                                    @RequestParam(required = false) String sign,
                                                    @RequestParam(required = false) String age,
                                                    @RequestParam(required = false) String profile,
                                                    @RequestParam(required = false) String folderId,
                                                    @RequestParam(required = false) MultipartFile photo,
                                                    @RequestParam(required = false) String photoRemove,
                                                    HttpSession session) {
        CharaService.Input in = new CharaService.Input();
        in.name = name;
        in.gender = gender;
        in.pref = pref;
        in.blood = blood;
        in.sign = sign;
        in.age = age;
        in.profile = profile;
        in.folderId = folderId;
        in.photo = photo;
        in.removePhoto = "1".equals(photoRemove);
        Long charaId = parseId(id);
        try {
            Object admin = session.getAttribute(AuthInterceptor.SESSION_ADMIN_NAME);
            Chara c = service.save(charaId, in, admin == null ? null : String.valueOf(admin));
            auditLog.record(AuditLogService.ACTION_SETTINGS_UPDATE, "Chara", c.getId(),
                    (charaId == null ? "キャラを登録: " : "キャラを更新: ") + c.getName());
            return ResponseEntity.ok(charaJson(c));
        } catch (CharaService.CharaException e) {
            return error(e.getMessage());
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<Map<String, Object>> delete(@RequestParam String id) {
        Long charaId = parseId(id);
        if (charaId == null) return error("IDが不正です");
        try {
            service.delete(charaId);
            auditLog.record(AuditLogService.ACTION_SETTINGS_UPDATE, "Chara", charaId, "キャラを削除");
            return ResponseEntity.ok(Collections.<String, Object>singletonMap("deleted", charaId));
        } catch (CharaService.CharaException e) {
            return error(e.getMessage());
        }
    }

    @PostMapping("/folders/save")
    public ResponseEntity<Map<String, Object>> saveFolder(@RequestParam(required = false) String id,
                                                          @RequestParam(required = false) String name) {
        try {
            return ResponseEntity.ok(folderJson(service.saveFolder(parseId(id), name)));
        } catch (CharaService.CharaException e) {
            return error(e.getMessage());
        }
    }

    @PostMapping("/folders/delete")
    public ResponseEntity<Map<String, Object>> deleteFolder(@RequestParam String id) {
        Long folderId = parseId(id);
        if (folderId == null) return error("IDが不正です");
        try {
            service.deleteFolder(folderId);
            return ResponseEntity.ok(Collections.<String, Object>singletonMap("deleted", folderId));
        } catch (CharaService.CharaException e) {
            return error(e.getMessage());
        }
    }

    private static Map<String, Object> folderJson(CharaFolder f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(f.getId()));
        m.put("name", f.getName());
        return m;
    }

    private static Map<String, Object> charaJson(Chara c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(c.getId()));
        m.put("name", c.getName());
        m.put("gender", c.getGender());
        m.put("pref", nz(c.getPref()));
        m.put("blood", nz(c.getBlood()));
        m.put("sign", nz(c.getSign()));
        m.put("age", c.getAge() == null ? "" : String.valueOf(c.getAge()));
        m.put("profile", nz(c.getProfile()));
        m.put("photo", nz(c.getPhotoUrl()));
        m.put("folderId", c.getFolderId() == null ? "" : String.valueOf(c.getFolderId()));
        m.put("createdAt", c.getCreatedAt() == null ? "" : c.getCreatedAt().format(ISO_MIN));
        return m;
    }

    private static ResponseEntity<Map<String, Object>> error(String message) {
        return ResponseEntity.badRequest().body(Collections.<String, Object>singletonMap("message", message));
    }

    private static Long parseId(String id) {
        if (id == null || id.trim().isEmpty()) return null;
        try { return Long.valueOf(id.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private String json(Object o) throws JsonProcessingException {
        // Safe inside <script type="application/json">: never let the data close the tag.
        return objectMapper.writeValueAsString(o).replace("</", "<\\/");
    }
}
