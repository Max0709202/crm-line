package com.crm.controller;

import com.crm.service.AuditLogService;
import com.crm.service.FolderSettingService;
import com.crm.service.RelayRoutingService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * サイト構成 › リレーサーバー設定 — ユーザーごとの送信経路 (the client-approved relay_servers.html):
 * relays in priority order with 対象条件 (フォルダ・入金回数), 使用中, and 疎通確認. The page posts
 * FormData and expects JSON; errors come back as {@code {"error": "..."}} with HTTP 400.
 */
@Controller
@RequestMapping("/manager/settings/relay-servers")
public class RelayServerController {

    private final RelayRoutingService routingService;
    private final FolderSettingService folderSettingService;
    private final AuditLogService auditLog;
    private final ObjectMapper objectMapper;

    public RelayServerController(RelayRoutingService routingService, FolderSettingService folderSettingService,
                                 AuditLogService auditLog, ObjectMapper objectMapper) {
        this.routingService = routingService;
        this.folderSettingService = folderSettingService;
        this.auditLog = auditLog;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String page(Model model) throws JsonProcessingException {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("ourIp", routingService.ourIp());
        cfg.put("bridgeIp", routingService.bridgeHost());
        cfg.put("folders", folderSettingService.listFolders());
        model.addAttribute("configJson", json(cfg));
        model.addAttribute("relaysJson", json(routingService.list()));
        return "setting/relay-servers";
    }

    @PostMapping("/save")
    public ResponseEntity<Object> save(@RequestParam(required = false) String id,
                                       @RequestParam(required = false) String name,
                                       @RequestParam(required = false) String ip,
                                       @RequestParam(required = false) String port,
                                       @RequestParam(name = "folders", required = false) List<String> folders,
                                       @RequestParam(required = false) String payMin,
                                       @RequestParam(required = false) String payMax,
                                       @RequestParam(required = false) String active,
                                       @RequestParam(required = false) String memo) {
        RelayRoutingService.Input in = new RelayRoutingService.Input();
        in.name = name;
        in.ip = ip;
        in.port = port;
        in.folders = folders;
        in.payMin = payMin;
        in.payMax = payMax;
        in.active = "1".equals(active);
        in.memo = memo;
        Long relayId = parseId(id);
        try {
            RelayRoutingService.View v = routingService.save(relayId, in);
            auditLog.record(AuditLogService.ACTION_SETTINGS_UPDATE, "RelayServer", Long.valueOf(v.id),
                    (relayId == null ? "リレーサーバーを追加: " : "リレーサーバーを更新: ") + v.name + " " + v.ip + ":" + v.port
                            + (v.active ? " 使用中" : " 停止中"));
            return ResponseEntity.ok(v);
        } catch (RelayRoutingService.RelayException e) {
            return error(e.getMessage());
        }
    }

    @PostMapping("/bulk-delete")
    public ResponseEntity<Object> delete(@RequestParam(name = "ids", required = false) List<Long> ids) {
        int n = routingService.delete(ids);
        auditLog.record(AuditLogService.ACTION_SETTINGS_UPDATE, "RelayServer", null, "リレーサーバーを削除: " + ids);
        return ResponseEntity.ok(Collections.singletonMap("deleted", n));
    }

    @PostMapping("/reorder")
    public ResponseEntity<Object> reorder(@RequestParam(name = "ids", required = false) List<Long> ids) {
        routingService.reorder(ids);
        auditLog.record(AuditLogService.ACTION_SETTINGS_UPDATE, "RelayServer", null, "リレーサーバーの優先順位を変更: " + ids);
        return ResponseEntity.ok(Collections.singletonMap("ok", true));
    }

    @PostMapping("/active")
    public ResponseEntity<Object> active(@RequestParam(required = false) String id,
                                         @RequestParam(required = false) String active) {
        Long relayId = parseId(id);
        if (relayId == null) return error("IDが不正です");
        try {
            routingService.setActive(relayId, "1".equals(active));
            auditLog.record(AuditLogService.ACTION_SETTINGS_UPDATE, "RelayServer", relayId,
                    "1".equals(active) ? "リレーサーバーを使用中に" : "リレーサーバーを停止");
            return ResponseEntity.ok(Collections.singletonMap("ok", true));
        } catch (RelayRoutingService.RelayException e) {
            return error(e.getMessage());
        }
    }

    @PostMapping("/check")
    public ResponseEntity<Object> check(@RequestParam(required = false) String id,
                                        @RequestParam(required = false) String ip,
                                        @RequestParam(required = false) String port) {
        try {
            RelayRoutingService.CheckResult r = routingService.check(parseId(id), ip, port);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", r.status);
            body.put("message", r.message);
            body.put("checkedAt", RelayRoutingService.isoMinutes(r.at));
            return ResponseEntity.ok(body);
        } catch (RelayRoutingService.RelayException e) {
            return error(e.getMessage());
        }
    }

    private static Long parseId(String id) {
        if (id == null || id.trim().isEmpty()) return null;
        try { return Long.valueOf(id.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static ResponseEntity<Object> error(String msg) {
        return ResponseEntity.badRequest().body(Collections.singletonMap("error", msg));
    }

    /** JSON for a {@code <script type="application/json">} block — "</" can't close the tag. */
    private String json(Object o) throws JsonProcessingException {
        return objectMapper.writeValueAsString(o).replace("</", "<\\/");
    }
}
