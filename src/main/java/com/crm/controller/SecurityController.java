package com.crm.controller;

import com.crm.interceptor.AdminIpInterceptor;
import com.crm.interceptor.AuthInterceptor;
import com.crm.service.AdminIpService;
import com.crm.service.AdminRoleService;
import com.crm.service.AuditLogService;
import com.crm.service.BackupService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * サイト構成 › セキュリティ設定 (formerly バックアップ設定; the client-approved design
 * security_settings.html): サーバー状況 (the 管理トップ server panel's values), 管理画面IP許可設定
 * ({@link AdminIpService}) and バックアップ設定 ({@link BackupService}). Menu key stays "backup", so
 * existing 権限設定 that hide it keep hiding this page.
 */
@Controller
@RequestMapping("/manager/settings/security")
public class SecurityController {

    private static final String MENU_KEY = "backup";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AdminIpService adminIpService;
    private final BackupService backupService;
    private final SystemStatsController systemStatsController;
    private final AdminRoleService roleService;
    private final AuditLogService auditLog;

    public SecurityController(AdminIpService adminIpService, BackupService backupService,
                              SystemStatsController systemStatsController, AdminRoleService roleService,
                              AuditLogService auditLog) {
        this.adminIpService = adminIpService;
        this.backupService = backupService;
        this.systemStatsController = systemStatsController;
        this.roleService = roleService;
        this.auditLog = auditLog;
    }

    @GetMapping
    public String page(HttpServletRequest request, Model model) throws JsonProcessingException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("currentIp", AdminIpInterceptor.clientIp(request));
        Map<String, Object> ip = new LinkedHashMap<>();
        ip.put("enabled", adminIpService.isEnabled());
        ip.put("list", adminIpService.list());
        data.put("ip", ip);
        BackupService.Config c = backupService.getConfig();
        Map<String, Object> bk = new LinkedHashMap<>();
        bk.put("enabled", c.enabled);
        bk.put("intervalHours", c.intervalHours);
        bk.put("retentionCount", c.retentionCount);
        // 前回の実行 = the latest attempt (with 成功 / 失敗); 次回 counts from the latest success
        java.time.LocalDateTime lastAttempt = c.lastResultAt != null ? c.lastResultAt : c.lastRunAt;
        bk.put("lastRunAt", lastAttempt == null ? "" : lastAttempt.withNano(0).toString());
        bk.put("lastResult", c.lastResult == null ? (c.lastRunAt == null ? "" : BackupService.RESULT_OK) : c.lastResult);
        bk.put("lastSuccessAt", c.lastRunAt == null ? "" : c.lastRunAt.withNano(0).toString());
        data.put("backup", bk);
        data.put("server", serverStatus());
        // JSON inside <script type="application/json">: keep "</" from closing the tag
        model.addAttribute("secJson", JSON.writeValueAsString(data).replace("</", "<\\/"));
        return "setting/security";
    }

    /** サーバー状況 for the page's 30-second refresh (same shape as the page's initial data). */
    @GetMapping("/server-status")
    @ResponseBody
    public Map<String, Object> serverStatusJson() {
        return serverStatus();
    }

    @PostMapping("/admin-ip")
    public Object saveAdminIp(@RequestParam(name = "enabled", defaultValue = "false") boolean enabled,
                              @RequestParam(name = "allowIps", required = false) List<String> allowIps,
                              @RequestParam(name = "allowMemos", required = false) List<String> allowMemos,
                              HttpSession session, RedirectAttributes ra) {
        if (hiddenForRole(session)) return ResponseEntity.status(403).body("この権限では変更できません");
        try {
            adminIpService.save(enabled, allowIps == null ? new ArrayList<>() : allowIps,
                    allowMemos == null ? new ArrayList<>() : allowMemos);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/manager/settings/security";
        }
        auditLog.record(AuditLogService.ACTION_SETTINGS_UPDATE, "AdminIp", null,
                "管理画面IP許可設定: " + (enabled ? "有効" : "無効") + " 許可IP=" + adminIpService.list().size() + "件");
        ra.addFlashAttribute("flashSuccess", "管理画面IP許可設定を保存しました");
        return "redirect:/manager/settings/security";
    }

    @PostMapping("/backup")
    public Object saveBackup(@RequestParam(name = "enabled", defaultValue = "false") boolean enabled,
                             @RequestParam(name = "intervalHours", defaultValue = "24") int intervalHours,
                             @RequestParam(name = "retentionCount", defaultValue = "7") int retentionCount,
                             HttpSession session, RedirectAttributes ra) {
        if (hiddenForRole(session)) return ResponseEntity.status(403).body("この権限では変更できません");
        backupService.saveConfig(enabled, intervalHours, retentionCount);
        auditLog.record(AuditLogService.ACTION_SETTINGS_UPDATE, "Backup", null,
                "バックアップ設定: " + (enabled ? "有効" : "無効") + " " + intervalHours + "時間ごと 保持" + retentionCount + "件");
        ra.addFlashAttribute("flashSuccess", "バックアップ設定を保存しました");
        return "redirect:/manager/settings/security";
    }

    @PostMapping("/backup/run-now")
    public Object runBackupNow(HttpSession session, RedirectAttributes ra) {
        if (hiddenForRole(session)) return ResponseEntity.status(403).body("この権限では実行できません");
        if (backupService.isRunning()) {
            ra.addFlashAttribute("flashError", "バックアップを実行中です。しばらくしてから確認してください");
        } else if (backupService.runBackupNow()) {
            ra.addFlashAttribute("flashSuccess", "バックアップを作成しました");
        } else {
            ra.addFlashAttribute("flashError", "バックアップに失敗しました（ディスクの空き容量・権限を確認してください）");
        }
        return "redirect:/manager/settings/security";
    }

    /** The logged-in 権限 hides セキュリティ設定 (the page is refused by RoleInterceptor; the posts here). */
    private boolean hiddenForRole(HttpSession session) {
        Object id = session.getAttribute(AuthInterceptor.SESSION_ADMIN_ID);
        if (!(id instanceof Long)) return true;
        Optional<AdminRoleService.RoleView> role = roleService.viewFor((Long) id);
        return role.isPresent() && role.get().isHidden(MENU_KEY);
    }

    /** 管理トップ's server panel values (SystemStatsController) in the page's units. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> serverStatus() {
        Map<String, Object> s = systemStatsController.stats();
        Map<String, Object> mem = (Map<String, Object>) s.get("memory");
        Map<String, Object> disk = (Map<String, Object>) s.get("disk");
        Map<String, Object> jvm = (Map<String, Object>) s.get("jvm");
        Map<String, Object> cpu = (Map<String, Object>) s.get("cpu");
        Map<String, Object> bk = (Map<String, Object>) s.get("backup");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("memUsedGB", gb(mem.get("usedBytes")));
        out.put("memTotalGB", gb(mem.get("totalBytes")));
        out.put("memPct", mem.get("usedPct"));
        out.put("swapUsedGB", gb(mem.get("swapUsedBytes")));
        out.put("swapTotalGB", gb(mem.get("swapTotalBytes")));
        out.put("swapPct", mem.get("swapUsedPct"));
        out.put("diskUsedGB", gb(disk.get("usedBytes")));
        out.put("diskTotalGB", gb(disk.get("totalBytes")));
        out.put("diskPct", disk.get("usedPct"));
        out.put("heapUsedMB", Math.round(num(jvm.get("heapUsedBytes")) / 1048576.0));
        out.put("heapMaxGB", gb(jvm.get("heapMaxBytes")));
        out.put("heapPct", jvm.get("heapUsedPct"));
        double load = num(cpu.get("load1"));
        out.put("load1", load < 0 ? 0 : Math.round(load * 100) / 100.0);
        out.put("cores", cpu.get("cores"));
        out.put("uptimeSec", jvm.get("uptimeSec"));
        long latestEpoch = (long) num(bk.get("latestEpoch"));
        if (latestEpoch > 0) {
            out.put("backupSizeMB", Math.max(0.1, Math.round(num(bk.get("latestBytes")) / 1048576.0 * 10) / 10.0));
            out.put("backupDaysAgo", (System.currentTimeMillis() - latestEpoch) / 86_400_000L);
        } else {
            out.put("backupSizeMB", null);
            out.put("backupDaysAgo", null);
        }
        // same rule as 管理トップ's 推定上限 hint; null = RAM増設の検討時期
        int memPct = (int) num(mem.get("usedPct")), swapPct = (int) num(mem.get("swapUsedPct"));
        Integer capacity = memPct >= 90 || swapPct >= 50 ? null
                : memPct >= 80 || swapPct >= 30 ? 50000 : memPct >= 60 ? 80000 : 100000;
        out.put("capacityUsers", capacity);
        out.put("checkedSecAgo", 0);
        return out;
    }

    private static double gb(Object bytes) {
        return Math.round(num(bytes) / 1073741824.0 * 10) / 10.0;
    }

    private static double num(Object v) {
        return v instanceof Number ? ((Number) v).doubleValue() : 0;
    }
}
