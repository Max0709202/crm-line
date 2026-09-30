package com.crm.controller;

import com.crm.service.AuditLogService;
import com.crm.service.FolderSettingService;
import com.crm.service.PointSettingService;
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

    public PointSettingController(PointSettingService pointSettingService,
                                  FolderSettingService folderSettingService, AuditLogService auditLog) {
        this.pointSettingService = pointSettingService;
        this.folderSettingService = folderSettingService;
        this.auditLog = auditLog;
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
