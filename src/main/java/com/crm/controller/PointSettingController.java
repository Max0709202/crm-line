package com.crm.controller;

import com.crm.service.AuditLogService;
import com.crm.service.FolderSettingService;
import com.crm.service.PointSettingService;
import com.crm.service.UserPointService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会員管理 › ポイント設定 — points consumed per member action (see {@link PointSettingService}).
 * The 共通 tab holds the shared values and 初期所持ポイント; each folder tab can switch that folder
 * to its own values.
 */
@Controller
@RequestMapping("/manager/settings/points")
public class PointSettingController {

    private static final String COST_PARAM_PREFIX = "cost_";

    private final PointSettingService pointSettingService;
    private final FolderSettingService folderSettingService;
    private final AuditLogService auditLog;
    private final UserPointService userPointService;

    public PointSettingController(PointSettingService pointSettingService,
                                  FolderSettingService folderSettingService, AuditLogService auditLog,
                                  UserPointService userPointService) {
        this.pointSettingService = pointSettingService;
        this.folderSettingService = folderSettingService;
        this.auditLog = auditLog;
        this.userPointService = userPointService;
    }

    /** 所持ポイント一括変更: set / add / subtract points for every user in a ユーザーID range. */
    @PostMapping("/bulk-change")
    public String bulkChange(@RequestParam(value = "fromId", required = false) Long fromId,
                             @RequestParam(value = "toId", required = false) Long toId,
                             @RequestParam(value = "mode", required = false) String mode,
                             @RequestParam(value = "amount", required = false) Integer amount,
                             RedirectAttributes ra) {
        UserPointService.BulkMode m;
        try {
            m = UserPointService.BulkMode.valueOf(mode == null ? "" : mode);
        } catch (IllegalArgumentException e) {
            m = null;
        }
        if (fromId == null || toId == null || fromId < 1 || toId < 1 || m == null
                || amount == null || amount < 0 || amount > UserPointService.MAX_POINTS) {
            ra.addFlashAttribute("flashError", "ユーザーIDの範囲・変更方法・ポイント（0〜"
                    + String.format("%,d", UserPointService.MAX_POINTS) + "）を正しく入力してください");
            return "redirect:/manager/settings/points";
        }
        int n = userPointService.bulkChange(fromId, toId, m, amount);
        String range = "ユーザーID " + Math.min(fromId, toId) + "〜" + Math.max(fromId, toId) + " の所持ポイント";
        String pt = String.format("%,d", amount) + "pt";
        String detail = m == UserPointService.BulkMode.SET ? range + "を " + pt + " に変更"
                : m == UserPointService.BulkMode.ADD ? range + "に " + pt + " 加算"
                : range + "から " + pt + " 減算";
        auditLog.record(AuditLogService.ACTION_SETTINGS_UPDATE, "UserPoint", null, detail + "（" + n + "名）");
        if (n == 0) {
            ra.addFlashAttribute("flashError", "指定したユーザーIDの範囲にユーザーがいません");
        } else {
            ra.addFlashAttribute("flashSuccess", detail + "しました（" + n + "名）");
        }
        return "redirect:/manager/settings/points";
    }

    @GetMapping
    public String form(@RequestParam(value = "folder", required = false) String folder, Model model) {
        List<String> folders = folderSettingService.listFolders();
        String f = folders.contains(folder) ? folder : null;
        // folder name → uses its own settings, for the tab badges
        Map<String, Boolean> ownByFolder = new LinkedHashMap<>();
        for (String name : folders) ownByFolder.put(name, pointSettingService.hasFolderSettings(name));
        model.addAttribute("folders", ownByFolder);
        model.addAttribute("folder", f);
        model.addAttribute("folderOwn", f != null && ownByFolder.get(f));
        model.addAttribute("rows", pointSettingService.listAll(f));
        model.addAttribute("shownRows", pointSettingService.listShown(f));
        model.addAttribute("initialPoints", pointSettingService.getInitialPoints());
        return "setting/points";
    }

    @PostMapping
    public String save(@RequestParam Map<String, String> params,
                       @RequestParam(value = "folder", required = false) String folder,
                       @RequestParam(value = "initialPoints", required = false) String initialPoints,
                       @RequestParam(value = "shown", required = false) List<String> shown,
                       RedirectAttributes ra) {
        Map<String, String> costs = new HashMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getKey().startsWith(COST_PARAM_PREFIX)) {
                costs.put(e.getKey().substring(COST_PARAM_PREFIX.length()), e.getValue());
            }
        }
        boolean isFolder = folder != null && !folder.isEmpty();
        if (isFolder && !folderSettingService.listFolders().contains(folder)) {
            ra.addFlashAttribute("flashError", "フォルダ「" + folder + "」が見つかりません");
            return "redirect:/manager/settings/points";
        }

        List<String> rejected = new ArrayList<>();
        String scope;
        try {
            if (isFolder) {
                boolean own = "true".equals(params.get("folderOwn"));
                rejected.addAll(pointSettingService.saveFolder(folder, own, costs, shown));
                scope = "フォルダ「" + folder + "」" + (own ? "" : "（共通設定を使用）");
            } else {
                if (!pointSettingService.saveInitialPoints(initialPoints)) rejected.add("初期所持ポイント");
                rejected.addAll(pointSettingService.saveAll(costs, shown));
                scope = "共通";
            }
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            ra.addAttribute("folder", folder);
            return "redirect:/manager/settings/points";
        }
        auditLog.record(AuditLogService.ACTION_SETTINGS_UPDATE, "PointSetting", null, "ポイント設定を更新: " + scope);
        if (rejected.isEmpty()) {
            ra.addFlashAttribute("flashSuccess", "ポイント設定を保存しました（" + scope + "）");
        } else {
            ra.addFlashAttribute("flashError", "0〜" + PointSettingService.MAX_COST
                    + " の数字で入力してください（変更されていません）: " + String.join("、", rejected));
        }
        if (isFolder) ra.addAttribute("folder", folder);
        return "redirect:/manager/settings/points";
    }
}
