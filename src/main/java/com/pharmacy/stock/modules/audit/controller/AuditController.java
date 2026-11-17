package com.pharmacy.stock.modules.audit.controller;

import com.pharmacy.stock.modules.audit.service.AuditService;
import com.pharmacy.stock.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Traçabilité globale (journal d'audit) — routes : /audit
 */
@Controller
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final UserService userService;

    @GetMapping
    public String list(@RequestParam(required = false) Long userId,
                       @RequestParam(required = false) String actionType,
                       @RequestParam(required = false) String entityAffected,
                       Model model) {
        model.addAttribute("logs", auditService.search(userId, actionType, entityAffected));
        model.addAttribute("users", userService.findAll());
        model.addAttribute("actionTypes", auditService.actionTypes());
        model.addAttribute("entitiesAffected", auditService.entitiesAffected());
        model.addAttribute("selectedUserId", userId);
        model.addAttribute("selectedActionType", actionType);
        model.addAttribute("selectedEntityAffected", entityAffected);
        return "pages/audit/list";
    }
}
