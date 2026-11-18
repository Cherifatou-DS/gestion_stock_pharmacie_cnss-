package com.pharmacy.stock.modules.supply.controller;

import com.pharmacy.stock.common.exception.BusinessException;
import com.pharmacy.stock.common.util.ClientIpResolver;
import com.pharmacy.stock.modules.audit.service.AuditService;
import com.pharmacy.stock.modules.entity.entity.StructureEntity;
import com.pharmacy.stock.modules.entity.service.StructureEntityService;
import com.pharmacy.stock.modules.product.service.ProductService;
import com.pharmacy.stock.modules.supply.dto.SupplyRequestDto;
import com.pharmacy.stock.modules.supply.dto.SupplyRequestFormDto;
import com.pharmacy.stock.modules.supply.dto.SupplyRequestLineFormDto;
import com.pharmacy.stock.modules.supply.dto.SupplyRequestProcessFormDto;
import com.pharmacy.stock.modules.supply.entity.SupplyRequestStatus;
import com.pharmacy.stock.modules.supply.service.SupplyRequestService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;

/**
 * Demandes d'approvisionnement internes (CM → PC, PC → DC, DC → DAAS) — routes : /supply-requests
 */
@Controller
@RequestMapping("/supply-requests")
@RequiredArgsConstructor
public class SupplyRequestController {

    private final SupplyRequestService supplyRequestService;
    private final StructureEntityService structureEntityService;
    private final ProductService productService;
    private final AuditService auditService;
    private final ClientIpResolver clientIpResolver;

    @GetMapping
    public String list(@RequestParam(required = false) Long requestingEntityId,
                       @RequestParam(required = false) String status,
                       @AuthenticationPrincipal CustomUserDetails currentUser,
                       Model model) {
        boolean restricted = com.pharmacy.stock.modules.user.security.RoleNames
                .isRestrictedToOwnEntity(currentUser.getRole());
        model.addAttribute("requests", supplyRequestService.findRequests(
                currentUser.getEntityId(), currentUser.getRole(), requestingEntityId, status));
        model.addAttribute("entities",
                restricted ? List.of() : structureEntityService.findByType("CENTRE_MEDICAL"));
        model.addAttribute("statuses", Arrays.asList(SupplyRequestStatus.values()));
        model.addAttribute("selectedRequestingEntityId", requestingEntityId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("restricted", restricted);
        return "pages/supply-requests/list";
    }

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        SupplyRequestFormDto form = new SupplyRequestFormDto();
        form.getLines().add(new SupplyRequestLineFormDto());
        model.addAttribute("requestForm", form);
        model.addAttribute("allowedSuppliers", allowedSuppliers(currentUser));
        model.addAttribute("products", productService.findAll());
        return "pages/supply-requests/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("requestForm") SupplyRequestFormDto form,
                         BindingResult result,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         HttpServletRequest request,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("allowedSuppliers", allowedSuppliers(currentUser));
            model.addAttribute("products", productService.findAll());
            return "pages/supply-requests/form";
        }
        try {
            SupplyRequestDto created = supplyRequestService.createRequest(form, currentUser.getId(),
                    currentUser.getRole(), currentUser.getEntityId());
            auditService.record(currentUser.getId(), "CREATION_DEMANDE", "SUPPLY_REQUESTS", created.getId(),
                    "Création de la demande d'approvisionnement " + created.getRequestNumber()
                            + " (" + created.getRequestingEntityCode() + " → " + created.getSupplyingEntityCode()
                            + ", " + created.getItems().size() + " ligne(s))",
                    clientIpResolver.resolve(request));
            redirectAttributes.addFlashAttribute("message",
                    "Demande " + created.getRequestNumber() + " créée à l'état Brouillon.");
            return "redirect:/supply-requests/" + created.getId();
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("allowedSuppliers", allowedSuppliers(currentUser));
            model.addAttribute("products", productService.findAll());
            return "pages/supply-requests/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         Model model) {
        SupplyRequestDto request = supplyRequestService.findRequestDetail(id, currentUser.getEntityId(),
                currentUser.getRole())
                .orElseThrow(() -> new BusinessException(
                        "Demande d'approvisionnement introuvable : id=" + id));
        model.addAttribute("request", request);
        model.addAttribute("processForm", new SupplyRequestProcessFormDto());
        addRequestActions(request, currentUser, model);
        return "pages/supply-requests/detail";
    }

    @PostMapping("/{id}/submit")
    public String submit(@PathVariable Long id,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        return executeAction(id, currentUser, request, redirectAttributes, "SOUMISSION_DEMANDE",
                "Soumission de la demande d'approvisionnement %s -> SOUMISE",
                () -> supplyRequestService.submitRequest(id, currentUser.getId(), currentUser.getRole()),
                r -> "Demande " + r.getRequestNumber() + " soumise à " + r.getSupplyingEntityName() + ".");
    }

    @PostMapping("/{id}/take-charge")
    public String takeCharge(@PathVariable Long id,
                             @AuthenticationPrincipal CustomUserDetails currentUser,
                             HttpServletRequest request,
                             RedirectAttributes redirectAttributes) {
        return executeAction(id, currentUser, request, redirectAttributes, "PRISE_EN_CHARGE",
                "Prise en charge de la demande %s -> EN_COURS_TRAITEMENT",
                () -> supplyRequestService.takeCharge(id, currentUser.getId(), currentUser.getRole()),
                r -> "Demande " + r.getRequestNumber() + " prise en charge.");
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id,
                         @ModelAttribute("processForm") SupplyRequestProcessFormDto form,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         HttpServletRequest request,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            SupplyRequestDto updated = supplyRequestService.rejectRequest(id, form.getRejectionReason(),
                    currentUser.getId(), currentUser.getRole());
            auditService.record(currentUser.getId(), "REJET_DEMANDE", "SUPPLY_REQUESTS", id,
                    "Rejet de la demande " + updated.getRequestNumber() + " (motif : "
                            + form.getRejectionReason() + ")",
                    clientIpResolver.resolve(request));
            redirectAttributes.addFlashAttribute("message",
                    "Demande " + updated.getRequestNumber() + " rejetée.");
            return "redirect:/supply-requests/{id}";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/supply-requests/{id}";
        }
    }

    @PostMapping("/{id}/ship")
    public String ship(@PathVariable Long id,
                       @ModelAttribute("processForm") SupplyRequestProcessFormDto form,
                       @AuthenticationPrincipal CustomUserDetails currentUser,
                       HttpServletRequest request,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        try {
            SupplyRequestDto updated = supplyRequestService.shipRequest(id, form, currentUser.getId(),
                    currentUser.getRole());
            auditService.record(currentUser.getId(), "EXPEDITION_DEMANDE", "SUPPLY_REQUESTS", id,
                    "Expédition de la demande " + updated.getRequestNumber()
                            + " (stock débité, sortie valorisée)",
                    clientIpResolver.resolve(request));
            redirectAttributes.addFlashAttribute("message",
                    "Demande " + updated.getRequestNumber() + " expédiée : stock débité chez "
                            + updated.getSupplyingEntityCode() + ".");
            return "redirect:/supply-requests/{id}";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/supply-requests/{id}";
        }
    }

    @PostMapping("/{id}/confirm-delivery")
    public String confirmDelivery(@PathVariable Long id,
                                  @AuthenticationPrincipal CustomUserDetails currentUser,
                                  HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
        return executeAction(id, currentUser, request, redirectAttributes, "LIVRAISON",
                "Confirmation de livraison de la demande %s -> LIVREE",
                () -> supplyRequestService.confirmDelivery(id, currentUser.getId(), currentUser.getRole(),
                        currentUser.getEntityId()),
                r -> "Demande " + r.getRequestNumber() + " livrée : stock crédité chez "
                        + r.getRequestingEntityCode() + ".");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface RequestAction {
        SupplyRequestDto run();
    }

    @FunctionalInterface
    private interface FlashMessage {
        String apply(SupplyRequestDto request);
    }

    private String executeAction(Long id, CustomUserDetails currentUser, HttpServletRequest request,
                                 RedirectAttributes redirectAttributes, String actionType,
                                 String auditTemplate, RequestAction action, FlashMessage successMessage) {
        try {
            SupplyRequestDto requestDto = action.run();
            auditService.record(currentUser.getId(), actionType, "SUPPLY_REQUESTS", id,
                    String.format(auditTemplate, requestDto.getRequestNumber()),
                    clientIpResolver.resolve(request));
            redirectAttributes.addFlashAttribute("message", successMessage.apply(requestDto));
            return "redirect:/supply-requests/{id}";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/supply-requests/{id}";
        }
    }

    /**
     * Structures sollicitées proposées selon la structure de rattachement :
     * CM → PC, PC → DC, DC → DAAS.
     */
    private List<StructureEntity> allowedSuppliers(CustomUserDetails currentUser) {
        List<StructureEntity> all = structureEntityService.findAll();
        String role = currentUser.getRole();
        return switch (role == null ? "" : role) {
            case "CHEF_SERVICE", "AGENT_APPROVISIONNEMENT" ->
                    all.stream().filter(e -> "PHARMACIE_CENTRALE".equals(e.getType())).toList();
            case "RESPONSABLE_PHARMACIE" ->
                    all.stream().filter(e -> "DEPOT_CENTRAL".equals(e.getType())).toList();
            case "RESPONSABLE_DEPOT", "MAGASINIER" ->
                    all.stream().filter(e -> "DAAS".equals(e.getType())).toList();
            case "ADMIN" ->
                    all.stream().filter(e -> List.of("PHARMACIE_CENTRALE", "DEPOT_CENTRAL", "DAAS")
                            .contains(e.getType())).toList();
            default -> List.of();
        };
    }

    /**
     * Indicateurs d'actions contextuels (statut + rôle) pour la vue de détail.
     */
    private void addRequestActions(SupplyRequestDto request, CustomUserDetails currentUser, Model model) {
        String role = currentUser.getRole();
        String status = request.getStatus();
        boolean isAdmin = "ADMIN".equals(role);
        String supplyingType = request.getSupplyingEntityType();
        boolean canProcessSupplying =
                ("PHARMACIE_CENTRALE".equals(supplyingType) && ("RESPONSABLE_PHARMACIE".equals(role) || isAdmin))
                        || ("DEPOT_CENTRAL".equals(supplyingType)
                            && ("RESPONSABLE_DEPOT".equals(role) || "MAGASINIER".equals(role) || isAdmin))
                        || ("DAAS".equals(supplyingType) && ("DAAS".equals(role) || isAdmin));

        boolean isDaasCircuit = request.isDaasCircuit();

        model.addAttribute("canSubmit", "BROUILLON".equals(status));
        model.addAttribute("canTakeCharge", "SOUMISE".equals(status) && canProcessSupplying);
        model.addAttribute("canReject",
                ("SOUMISE".equals(status) || "EN_COURS_TRAITEMENT".equals(status)) && canProcessSupplying);
        model.addAttribute("canShip",
                !isDaasCircuit
                        && ("SOUMISE".equals(status) || "EN_COURS_TRAITEMENT".equals(status))
                        && canProcessSupplying);
        model.addAttribute("canAwaitOrder", isDaasCircuit && canProcessSupplying);
        model.addAttribute("canConfirmDelivery", "EXPEDIEE".equals(status)
                && (isAdmin || request.getRequestingEntityId().equals(currentUser.getEntityId())));
    }
}
