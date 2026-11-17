package com.pharmacy.stock.modules.entity.controller;

import com.pharmacy.stock.common.exception.BusinessException;
import com.pharmacy.stock.modules.audit.service.AuditService;
import com.pharmacy.stock.modules.entity.dto.EntityFormDto;
import com.pharmacy.stock.modules.entity.service.StructureEntityService;
import com.pharmacy.stock.modules.user.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structures de référence (CM, PC, DC, DBI, Fournisseur) — routes : /entities (rôle ADMIN)
 */
@Controller
@RequestMapping("/entities")
@RequiredArgsConstructor
public class EntityController {

    static final Map<String, String> STRUCTURE_TYPES = Map.of(
            "CENTRE_MEDICAL", "Centre Médical",
            "PHARMACIE_CENTRALE", "Pharmacie Centrale",
            "DEPOT_CENTRAL", "Dépôt Central",
            "DBI", "Direction des Biens et Inventaires",
            "FOURNISSEUR", "Fournisseur");

    private final StructureEntityService structureEntityService;
    private final AuditService auditService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("entities", structureEntityService.findAll());
        model.addAttribute("activeCount", structureEntityService.findAllActive().size());
        return "pages/entities/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("entityForm", new EntityFormDto());
        model.addAttribute("structureTypes", structureTypes());
        model.addAttribute("readonly", false);
        return "pages/entities/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("entityForm") EntityFormDto form,
                         BindingResult result,
                         Model model,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("structureTypes", structureTypes());
            model.addAttribute("readonly", false);
            return "pages/entities/form";
        }
        try {
            var created = structureEntityService.createEntity(form);
            auditService.record(currentUser.getId(), "CREATION", "ENTITIES", created.getId(),
                    "Création de la structure " + created.getCode() + " (" + created.getName()
                            + ", type " + created.getType() + ")",
                    request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("message",
                    "Structure " + created.getCode() + " créée avec succès.");
            return "redirect:/entities";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("structureTypes", structureTypes());
            model.addAttribute("readonly", false);
            return "pages/entities/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        var entity = structureEntityService.findById(id)
                .orElseThrow(() -> new BusinessException("Structure introuvable : id=" + id));
        EntityFormDto form = new EntityFormDto();
        form.setId(entity.getId());
        form.setCode(entity.getCode());
        form.setName(entity.getName());
        form.setType(entity.getType());
        model.addAttribute("entityForm", form);
        model.addAttribute("structureTypes", structureTypes());
        model.addAttribute("entityId", id);
        model.addAttribute("readonly", false);
        return "pages/entities/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("entityForm") EntityFormDto form,
                         BindingResult result,
                         Model model,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("structureTypes", structureTypes());
            model.addAttribute("entityId", id);
            model.addAttribute("readonly", false);
            return "pages/entities/form";
        }
        try {
            var updated = structureEntityService.updateEntity(id, form);
            auditService.record(currentUser.getId(), "MODIFICATION", "ENTITIES", updated.getId(),
                    "Modification de la structure " + updated.getCode() + " (" + updated.getName()
                            + ", type " + updated.getType() + ")",
                    request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("message",
                    "Structure " + updated.getCode() + " modifiée avec succès.");
            return "redirect:/entities";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("structureTypes", structureTypes());
            model.addAttribute("entityId", id);
            model.addAttribute("readonly", false);
            return "pages/entities/form";
        }
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id,
                             @AuthenticationPrincipal CustomUserDetails currentUser,
                             HttpServletRequest request,
                             RedirectAttributes redirectAttributes) {
        try {
            var entity = structureEntityService.deactivateEntity(id);
            auditService.record(currentUser.getId(), "DEACTIVATION", "ENTITIES", entity.getId(),
                    "Désactivation de la structure " + entity.getCode() + " (" + entity.getName()
                            + ", type " + entity.getType() + ")",
                    request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("message",
                    "Structure " + entity.getCode() + " désactivée avec succès.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/entities";
    }

    @PostMapping("/{id}/reactivate")
    public String reactivate(@PathVariable Long id,
                             @AuthenticationPrincipal CustomUserDetails currentUser,
                             HttpServletRequest request,
                             RedirectAttributes redirectAttributes) {
        try {
            var entity = structureEntityService.reactivateEntity(id);
            auditService.record(currentUser.getId(), "REACTIVATION", "ENTITIES", entity.getId(),
                    "Réactivation de la structure " + entity.getCode() + " (" + entity.getName()
                            + ", type " + entity.getType() + ")",
                    request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("message",
                    "Structure " + entity.getCode() + " réactivée avec succès.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/entities";
    }

    private static Map<String, String> structureTypes() {
        return new LinkedHashMap<>(STRUCTURE_TYPES);
    }
}
