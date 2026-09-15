package com.crm.controller;

import com.crm.service.LineUserLinkService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Manual linking screen for LINE contacts LINE's webhook couldn't auto-match to a customer.
 * Not admin-gated — linking a contact is an ordinary operational task (like the rest of
 * customer management), unlike LINE account/credential settings in {@link LineAccountController}.
 */
@Controller
@RequestMapping("/manager/line-settings/unmatched")
public class LineUserLinkController {

    private final LineUserLinkService service;

    public LineUserLinkController(LineUserLinkService service) {
        this.service = service;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("unmatched", service.listUnlinked());
        return "line/unmatched-contacts";
    }

    @PostMapping("/{id}/link")
    public String link(@PathVariable Long id, @RequestParam Long crmUserId, RedirectAttributes ra) {
        try {
            service.link(id, crmUserId);
            ra.addFlashAttribute("flashSuccess", "顧客と紐付けました");
        } catch (LineUserLinkService.NotFoundException | LineUserLinkService.CrmUserNotFoundException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/manager/line-settings/unmatched";
    }
}
