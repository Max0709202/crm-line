package com.crm.controller;

import com.crm.service.AuditLogService;
import com.crm.service.FolderSettingService;
import com.crm.service.PaymentSettingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * サイト構成 › 決済関連設定 — per payment method: shown on ポイント購入 or not, and its plans
 * (表示金額 / ポイント). 共通 tab + one tab per folder, like ポイント設定
 * (see {@link PaymentSettingService}).
 *
 * Form fields: {@code order} (method codes, comma-separated, in display order),
 * {@code m_<method>_shown}, {@code m_<method>_label}, and per plan row i (0-based)
 * {@code p_<method>_<i>_shown}, {@code p_<method>_<i>_amount}, {@code p_<method>_<i>_points}.
 */
@Controller
@RequestMapping("/manager/settings/payments")
public class PaymentSettingController {

    private final PaymentSettingService paymentSettingService;
    private final FolderSettingService folderSettingService;
    private final AuditLogService auditLog;

    public PaymentSettingController(PaymentSettingService paymentSettingService,
                                    FolderSettingService folderSettingService, AuditLogService auditLog) {
        this.paymentSettingService = paymentSettingService;
        this.folderSettingService = folderSettingService;
        this.auditLog = auditLog;
    }

    @GetMapping
    public String form(@RequestParam(value = "folder", required = false) String folder, Model model) {
        List<String> folders = folderSettingService.listFolders();
        String f = folders.contains(folder) ? folder : null;
        Map<String, Boolean> ownByFolder = new LinkedHashMap<>();
        for (String name : folders) ownByFolder.put(name, paymentSettingService.hasFolderSettings(name));
        model.addAttribute("folders", ownByFolder);
        model.addAttribute("folder", f);
        model.addAttribute("folderOwn", f != null && ownByFolder.get(f));
        model.addAttribute("methods", paymentSettingService.getMethods(f));
        model.addAttribute("maxAmount", PaymentSettingService.MAX_AMOUNT);
        model.addAttribute("maxPoints", PaymentSettingService.MAX_POINTS);
        model.addAttribute("maxLabel", PaymentSettingService.MAX_LABEL);
        return "setting/payments";
    }

    @PostMapping
    public String save(@RequestParam Map<String, String> params,
                       @RequestParam(value = "folder", required = false) String folder,
                       RedirectAttributes ra) {
        boolean isFolder = folder != null && !folder.isEmpty();
        if (isFolder && !folderSettingService.listFolders().contains(folder)) {
            ra.addFlashAttribute("flashError", "フォルダ「" + folder + "」が見つかりません");
            return "redirect:/manager/settings/payments";
        }

        Map<String, PaymentSettingService.MethodInput> input = new LinkedHashMap<>();
        for (String code : PaymentSettingService.METHODS.keySet()) {
            List<PaymentSettingService.PlanInput> plans = new ArrayList<>();
            for (int i = 0; i < PaymentSettingService.PLAN_ROWS; i++) {
                String prefix = "p_" + code + "_" + i + "_";
                plans.add(new PaymentSettingService.PlanInput("true".equals(params.get(prefix + "shown")),
                        params.get(prefix + "amount"), params.get(prefix + "points")));
            }
            input.put(code, new PaymentSettingService.MethodInput("true".equals(params.get("m_" + code + "_shown")),
                    params.get("m_" + code + "_label"), plans));
        }
        String orderParam = params.get("order");
        List<String> order = orderParam == null ? null : java.util.Arrays.asList(orderParam.split(","));

        List<String> rejected;
        String scope;
        try {
            if (isFolder) {
                boolean own = "true".equals(params.get("folderOwn"));
                rejected = paymentSettingService.saveFolder(folder, own, input, order);
                scope = "フォルダ「" + folder + "」" + (own ? "" : "（共通設定を使用）");
            } else {
                rejected = paymentSettingService.saveCommon(input, order);
                scope = "共通";
            }
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            if (isFolder) ra.addAttribute("folder", folder);
            return "redirect:/manager/settings/payments";
        }
        auditLog.record(AuditLogService.ACTION_SETTINGS_UPDATE, "PaymentSetting", null, "決済関連設定を更新: " + scope);
        if (rejected.isEmpty()) {
            ra.addFlashAttribute("flashSuccess", "決済関連設定を保存しました（" + scope + "）");
        } else {
            ra.addFlashAttribute("flashError", "表示金額は1〜" + String.format("%,d", PaymentSettingService.MAX_AMOUNT)
                    + "円、ポイントは0〜" + String.format("%,d", PaymentSettingService.MAX_POINTS)
                    + "の数字を両方入力してください（その行は変更されていません）: " + String.join("、", rejected));
        }
        if (isFolder) ra.addAttribute("folder", folder);
        return "redirect:/manager/settings/payments";
    }
}
