package com.crm.controller;

import com.crm.service.AuditLogService;
import com.crm.service.PaymentReportService;
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

/** 入金レポート — listed under 管理トップ in the sidebar (moved off the dashboard 2026-10-01). */
@Controller
@RequestMapping("/manager/reports/payments")
public class PaymentReportController {

    private final PaymentReportService reportService;
    private final AuditLogService auditLog;
    private final ObjectMapper objectMapper;

    public PaymentReportController(PaymentReportService reportService,
                                   AuditLogService auditLog,
                                   ObjectMapper objectMapper) {
        this.reportService = reportService;
        this.auditLog = auditLog;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String report(Model model) {
        model.addAttribute("rows", reportService.rows());
        String loginJson;
        try {
            loginJson = objectMapper.writeValueAsString(reportService.loginCounts());
        } catch (JsonProcessingException e) {
            loginJson = "{}";
        }
        model.addAttribute("loginJson", loginJson);
        model.addAttribute("todayLogins", reportService.todayLoginCount());
        model.addAttribute("memoMax", PaymentReportService.MEMO_MAX);
        return "report/payments";
    }

    @PostMapping("/memo")
    @ResponseBody
    public ResponseEntity<String> saveMemo(@RequestParam Long id, @RequestParam(required = false) String memo) {
        return updateMemo(id, memo);
    }

    @PostMapping("/memo/delete")
    @ResponseBody
    public ResponseEntity<String> deleteMemo(@RequestParam Long id) {
        return updateMemo(id, null);
    }

    private ResponseEntity<String> updateMemo(Long id, String memo) {
        try {
            reportService.saveMemo(id, memo);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        auditLog.record(AuditLogService.ACTION_PAYMENT_UPDATE, "Payment", id,
                memo == null || memo.trim().isEmpty() ? "memo cleared" : "memo updated");
        return ResponseEntity.ok("ok");
    }
}
