package com.crm.controller;

import com.crm.entity.CrmUser;
import com.crm.entity.SupportInquiry;
import com.crm.entity.SupportReply;
import com.crm.entity.SupportTemplate;
import com.crm.interceptor.AuthInterceptor;
import com.crm.repository.CrmUserRepository;
import com.crm.service.AdminAuthService;
import com.crm.service.SupportDeskService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** サポート窓口 (client request 2026-10-02). */
@Controller
@RequestMapping("/manager/support")
public class SupportDeskController {

    private static final DateTimeFormatter ISO_MIN = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final SupportDeskService service;
    private final AdminAuthService adminAuthService;
    private final ObjectMapper objectMapper;
    private final CrmUserRepository userRepository;

    public SupportDeskController(SupportDeskService service, AdminAuthService adminAuthService, ObjectMapper objectMapper,
                                 CrmUserRepository userRepository) {
        this.service = service;
        this.adminAuthService = adminAuthService;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String page(Model model, HttpSession session) throws JsonProcessingException {
        List<SupportInquiry> inquiries = service.listInquiries();
        List<Long> ids = new ArrayList<>();
        for (SupportInquiry q : inquiries) ids.add(q.getId());
        Map<Long, List<SupportReply>> replies = service.repliesByInquiry(ids);
        // ユーザーID shown = the one on ユーザー詳細 (ログインID, else the internal ID)
        Set<Long> memberIds = new HashSet<>();
        for (SupportInquiry q : inquiries) if (q.getMemberId() != null) memberIds.add(q.getMemberId());
        Map<Long, String> memberNo = new HashMap<>();
        for (CrmUser u : userRepository.findAllById(memberIds)) {
            memberNo.put(u.getId(), u.getLoginId() != null && !u.getLoginId().isEmpty() ? u.getLoginId() : String.valueOf(u.getId()));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Map<String, String>> tagVals = new LinkedHashMap<>();
        for (SupportInquiry q : inquiries) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(q.getId()));
            m.put("channel", q.getChannel());
            m.put("name", nz(q.getName()));
            m.put("email", nz(q.getEmail()));
            m.put("subject", nz(q.getSubject()));
            m.put("body", nz(q.getBody()));
            m.put("receivedAt", q.getReceivedAt().format(ISO_MIN));
            m.put("status", q.getStatus());
            m.put("memberId", q.getMemberId() == null ? null : String.valueOf(q.getMemberId()));
            m.put("memberNo", q.getMemberId() == null ? null : memberNo.getOrDefault(q.getMemberId(), String.valueOf(q.getMemberId())));
            m.put("to", nz(q.getToAddress()));
            m.put("formType", nz(q.getFormType()));
            m.put("orderNo", nz(q.getOrderNo()));
            m.put("formPage", nz(q.getFormPage()));
            List<Map<String, String>> rs = new ArrayList<>();
            for (SupportReply r : replies.getOrDefault(q.getId(), Collections.emptyList())) {
                Map<String, String> x = new LinkedHashMap<>();
                x.put("at", r.getCreatedAt().format(ISO_MIN));
                x.put("staff", nz(r.getStaffName()));
                x.put("subject", nz(r.getSubject()));
                x.put("body", nz(r.getBody()));
                rs.add(x);
            }
            m.put("replies", rs);
            rows.add(m);
            if (q.getTagAmount() != null || q.getTagProduct() != null || q.getTagFullAddress() != null || q.getTagDateJp() != null) {
                Map<String, String> t = new LinkedHashMap<>();
                t.put("amount", nz(q.getTagAmount()));
                t.put("product", nz(q.getTagProduct()));
                t.put("fullAddress", nz(q.getTagFullAddress()));
                t.put("dateJp", nz(q.getTagDateJp()));
                tagVals.put(String.valueOf(q.getId()), t);
            }
        }
        List<Map<String, String>> tpls = new ArrayList<>();
        for (SupportTemplate t : service.templates()) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(t.getId()));
            m.put("title", nz(t.getTitle()));
            m.put("body", nz(t.getBody()));
            tpls.add(m);
        }
        model.addAttribute("supportAddress", service.supportAddress());
        model.addAttribute("senderName", service.senderName());
        model.addAttribute("senderNameMax", SupportDeskService.SENDER_NAME_MAX);
        model.addAttribute("inquiriesJson", json(rows));
        model.addAttribute("templatesJson", json(tpls));
        model.addAttribute("tagValsJson", json(tagVals));
        model.addAttribute("staffName", adminName(session));
        model.addAttribute("defaultTemplates", SupportDeskService.DEFAULT_TEMPLATES);
        return "support/desk";
    }

    @PostMapping("/reply")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> reply(@RequestParam(name = "ids", required = false) List<String> ids,
                                                     @RequestParam(required = false) String subject,
                                                     @RequestParam(required = false) String body,
                                                     @RequestParam(required = false) String markDone,
                                                     HttpSession session) {
        if (body == null || body.trim().isEmpty()) return error("本文を入力してください");
        if (body.length() > SupportDeskService.BODY_MAX) return error("本文が長すぎます");
        if (subject != null && subject.length() > SupportDeskService.SUBJECT_MAX) return error("件名が長すぎます");
        List<Long> idList = SupportDeskService.parseIds(ids);
        if (idList.isEmpty()) return error("宛先がありません");
        SupportDeskService.ReplyOutcome out = service.reply(idList, subject, body, "1".equals(markDone), adminName(session));
        Map<String, Object> res = new HashMap<>();
        List<String> sent = new ArrayList<>();
        for (Long id : out.sentIds) sent.add(String.valueOf(id));
        Map<String, String> failed = new LinkedHashMap<>();
        for (Map.Entry<Long, String> e : out.failed.entrySet()) failed.put(String.valueOf(e.getKey()), e.getValue());
        res.put("sent", sent);
        res.put("failed", failed);
        res.put("at", LocalDateTime.now().format(ISO_MIN));
        return ResponseEntity.ok(res);
    }

    /** 送信者名 (From display name of replies) — kept until changed. */
    @PostMapping("/sender-name")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveSenderName(@RequestParam(required = false) String name) {
        if (name != null && name.trim().length() > SupportDeskService.SENDER_NAME_MAX) {
            return error("送信者名は" + SupportDeskService.SENDER_NAME_MAX + "文字までです");
        }
        service.saveSenderName(name);
        return ResponseEntity.ok(Collections.singletonMap("name", service.senderName()));
    }

    @PostMapping("/bulk-delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> bulkDelete(@RequestParam(name = "ids", required = false) List<String> ids,
                                                          @RequestParam(required = false) String confirmPassword,
                                                          HttpSession session) {
        Long adminId = (Long) session.getAttribute(AuthInterceptor.SESSION_ADMIN_ID);
        if (!adminAuthService.verifyPassword(adminId, confirmPassword)) {
            return ResponseEntity.status(403).body(Collections.singletonMap("message", "管理者パスワードが正しくありません"));
        }
        int n = service.delete(SupportDeskService.parseIds(ids));
        return ResponseEntity.ok(Collections.singletonMap("deleted", n));
    }

    @PostMapping("/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> status(@RequestParam Long id, @RequestParam String status) {
        return service.setStatus(id, status) ? ResponseEntity.ok(Collections.singletonMap("ok", true)) : error("変更できませんでした");
    }

    @PostMapping("/templates")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveTemplate(@RequestParam(required = false) String id,
                                                            @RequestParam(required = false) String title,
                                                            @RequestParam(required = false) String body) {
        Long tid = null;
        if (id != null && !id.trim().isEmpty()) {
            try { tid = Long.parseLong(id.trim()); } catch (NumberFormatException e) { return error("不正なIDです"); }
        }
        SupportTemplate t = service.saveTemplate(tid, title, body);
        if (t == null) return error("定型文が見つかりません");
        return ResponseEntity.ok(Collections.singletonMap("id", String.valueOf(t.getId())));
    }

    @PostMapping("/templates/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteTemplate(@RequestParam Long id) {
        return service.deleteTemplate(id) ? ResponseEntity.ok(Collections.singletonMap("ok", true)) : error("この定型文は削除できません");
    }

    @PostMapping("/tags")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveTags(@RequestParam(name = "ids", required = false) List<String> ids,
                                                        @RequestParam(required = false) String amount,
                                                        @RequestParam(required = false) String product,
                                                        @RequestParam(required = false) String fullAddress,
                                                        @RequestParam(required = false) String dateJp) {
        int n = service.saveTags(SupportDeskService.parseIds(ids), amount, product, fullAddress, dateJp);
        return ResponseEntity.ok(Collections.singletonMap("saved", n));
    }

    private String json(Object o) throws JsonProcessingException {
        // Safe inside <script type="application/json">: never let the data close the tag.
        return objectMapper.writeValueAsString(o).replace("</", "<\\/");
    }

    private static ResponseEntity<Map<String, Object>> error(String message) {
        return ResponseEntity.badRequest().body(Collections.singletonMap("message", message));
    }

    private static String adminName(HttpSession session) {
        Object n = session == null ? null : session.getAttribute(AuthInterceptor.SESSION_ADMIN_NAME);
        return n == null ? "" : n.toString();
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
