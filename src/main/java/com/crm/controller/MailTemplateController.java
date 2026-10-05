package com.crm.controller;

import com.crm.service.AuditLogService;
import com.crm.service.MailTemplateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;

/**
 * サイト構成 › メールテンプレート設定 (the client-approved mail_templates.html): 仮登録通知・本登録通知・
 * 決済入金通知・メール通知 — subject / body, 有効・無効, テスト送信. JSON replies; errors are
 * {@code {"error": "..."}} with HTTP 400.
 */
@Controller
@RequestMapping("/manager/settings/mail-templates")
public class MailTemplateController {

    private final MailTemplateService service;
    private final AuditLogService auditLog;
    private final ObjectMapper objectMapper;

    public MailTemplateController(MailTemplateService service, AuditLogService auditLog, ObjectMapper objectMapper) {
        this.service = service;
        this.auditLog = auditLog;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String page(Model model) throws JsonProcessingException {
        model.addAttribute("tagsJson", json(service.tags()));
        model.addAttribute("templatesJson", json(service.list()));
        return "setting/mail-templates";
    }

    @PostMapping("/save")
    public ResponseEntity<Object> save(@RequestParam(required = false) String key,
                                       @RequestParam(required = false) String subject,
                                       @RequestParam(required = false) String body) {
        try {
            String at = service.save(key, subject, body);
            auditLog.record(AuditLogService.ACTION_SETTINGS_UPDATE, "MailTemplate", null, "メールテンプレートを保存: " + key);
            return ResponseEntity.ok(Collections.singletonMap("updatedAt", at));
        } catch (MailTemplateService.TemplateException e) {
            return error(e.getMessage());
        }
    }

    @PostMapping("/enabled")
    public ResponseEntity<Object> enabled(@RequestParam(required = false) String key,
                                          @RequestParam(required = false) String enabled) {
        try {
            service.setEnabled(key, "1".equals(enabled));
            auditLog.record(AuditLogService.ACTION_SETTINGS_UPDATE, "MailTemplate", null,
                    "メールテンプレートを" + ("1".equals(enabled) ? "有効" : "無効") + "に: " + key);
            return ResponseEntity.ok(Collections.singletonMap("ok", true));
        } catch (MailTemplateService.TemplateException e) {
            return error(e.getMessage());
        }
    }

    @PostMapping("/test")
    public ResponseEntity<Object> test(@RequestParam(required = false) String key,
                                       @RequestParam(required = false) String to,
                                       @RequestParam(required = false) String subject,
                                       @RequestParam(required = false) String body) {
        try {
            service.sendTest(key, to, subject, body);
            auditLog.record(AuditLogService.ACTION_SETTINGS_UPDATE, "MailTemplate", null, "メールテンプレートをテスト送信: " + key);
            return ResponseEntity.ok(Collections.singletonMap("ok", true));
        } catch (MailTemplateService.TemplateException e) {
            return error(e.getMessage());
        }
    }

    private static ResponseEntity<Object> error(String msg) {
        return ResponseEntity.badRequest().body(Collections.singletonMap("error", msg));
    }

    private String json(Object o) throws JsonProcessingException {
        return objectMapper.writeValueAsString(o).replace("</", "<\\/");
    }
}
