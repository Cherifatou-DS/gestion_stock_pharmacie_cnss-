package com.pharmacy.stock.modules.order.controller;

import com.pharmacy.stock.common.exception.BusinessException;
import com.pharmacy.stock.common.util.ClientIpResolver;
import com.pharmacy.stock.modules.audit.service.AuditService;
import com.pharmacy.stock.modules.entity.entity.StructureEntity;
import com.pharmacy.stock.modules.entity.service.StructureEntityService;
import com.pharmacy.stock.modules.order.dto.OrderAdjustmentFormDto;
import com.pharmacy.stock.modules.order.dto.OrderDto;
import com.pharmacy.stock.modules.order.dto.OrderFormDto;
import com.pharmacy.stock.modules.order.dto.OrderLineFormDto;
import com.pharmacy.stock.modules.order.dto.OrderReceptionFormDto;
import com.pharmacy.stock.modules.order.dto.OrderRejectionFormDto;
import com.pharmacy.stock.modules.order.entity.OrderStatus;
import com.pharmacy.stock.modules.order.service.OrderService;
import com.pharmacy.stock.modules.product.service.ProductService;
import com.pharmacy.stock.modules.supply.dto.SupplyRequestDto;
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
 * Circuit des commandes fournisseurs de la DAAS — routes : /orders
 * (création DAAS, validation DBI, réception Dépôt Central).
 */
@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final StructureEntityService structureEntityService;
    private final ProductService productService;
    private final SupplyRequestService supplyRequestService;
    private final AuditService auditService;
    private final ClientIpResolver clientIpResolver;

    @GetMapping
    public String list(@RequestParam(required = false) Long supplierEntityId,
                       @RequestParam(required = false) String status,
                       @AuthenticationPrincipal CustomUserDetails currentUser,
                       Model model) {
        model.addAttribute("orders", orderService.findOrders(
                currentUser.getEntityId(), currentUser.getRole(), supplierEntityId, status));
        model.addAttribute("suppliers", structureEntityService.findByType("FOURNISSEUR"));
        model.addAttribute("statuses", Arrays.asList(OrderStatus.values()));
        model.addAttribute("selectedSupplierEntityId", supplierEntityId);
        model.addAttribute("selectedStatus", status);
        return "pages/orders/list";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long supplyRequestId,
                          @AuthenticationPrincipal CustomUserDetails currentUser,
                          Model model) {
        OrderFormDto form = new OrderFormDto();
        form.getLines().add(new OrderLineFormDto());
        if (supplyRequestId != null) {
            supplyRequestService.findRequestDetail(supplyRequestId, currentUser.getEntityId(),
                    currentUser.getRole())
                    .ifPresent(request -> {
                        form.setSupplyRequestId(request.getId());
                        form.getLines().clear();
                        request.getItems().forEach(item ->
                                form.getLines().add(new OrderLineFormDto(item.getProductId(),
                                        item.getQuantityRequested())));
                        model.addAttribute("linkedRequest", request);
                    });
        }
        model.addAttribute("orderForm", form);
        model.addAttribute("suppliers", structureEntityService.findByType("FOURNISSEUR"));
        model.addAttribute("products", productService.findAll());
        model.addAttribute("openSupplyRequests", openSupplyRequests(currentUser));
        return "pages/orders/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("orderForm") OrderFormDto form,
                         BindingResult result,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         HttpServletRequest request,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("suppliers", structureEntityService.findByType("FOURNISSEUR"));
            model.addAttribute("products", productService.findAll());
            model.addAttribute("openSupplyRequests", openSupplyRequests(currentUser));
            return "pages/orders/form";
        }
        try {
            OrderDto created = orderService.createOrder(form, currentUser.getId(),
                    currentUser.getRole(), currentUser.getEntityId());
            auditService.record(currentUser.getId(), "CREATION_COMMANDE", "ORDERS", created.getId(),
                    "Création de la commande fournisseur " + created.getOrderNumber()
                            + " (" + created.getSupplierEntityName() + ", " + created.getItems().size()
                            + " ligne(s)"
                            + (created.getSupplyRequestNumber() != null
                                    ? ", demande " + created.getSupplyRequestNumber() : "")
                            + ")",
                    clientIpResolver.resolve(request));
            redirectAttributes.addFlashAttribute("message",
                    "Commande " + created.getOrderNumber() + " créée à l'état Brouillon.");
            return "redirect:/orders/" + created.getId();
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("suppliers", structureEntityService.findByType("FOURNISSEUR"));
            model.addAttribute("products", productService.findAll());
            model.addAttribute("openSupplyRequests", openSupplyRequests(currentUser));
            return "pages/orders/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         Model model) {
        OrderDto order = orderService.findOrderDetail(id, currentUser.getEntityId(), currentUser.getRole())
                .orElseThrow(() -> new BusinessException("Commande introuvable : id=" + id));
        model.addAttribute("order", order);
        model.addAttribute("adjustmentForm", new OrderAdjustmentFormDto());
        model.addAttribute("rejectionForm", new OrderRejectionFormDto());
        model.addAttribute("receptionForm", new OrderReceptionFormDto());
        addOrderActions(order, currentUser, model);
        return "pages/orders/detail";
    }

    @PostMapping("/{id}/submit")
    public String submit(@PathVariable Long id,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        return executeOrderAction(id, currentUser, request, redirectAttributes, "SOUMISSION",
                "Soumission de la commande %s -> EN_ATTENTE_DBI",
                () -> orderService.submitOrder(id, currentUser.getId(), currentUser.getRole()),
                order -> "Commande " + order.getOrderNumber() + " soumise à la validation DBI.");
    }

    @PostMapping("/{id}/adjust")
    public String adjust(@PathVariable Long id,
                         @Valid @ModelAttribute("adjustmentForm") OrderAdjustmentFormDto form,
                         BindingResult result,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         HttpServletRequest request,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return backToDetailWithOrder(id, currentUser, model);
        }
        try {
            OrderDto order = orderService.adjustOrder(id, form, currentUser.getId(), currentUser.getRole());
            auditService.record(currentUser.getId(), "REAJUSTEMENT", "ORDERS", id,
                    "Réajustement des quantités de la commande " + order.getOrderNumber() + " par la DBI",
                    clientIpResolver.resolve(request));
            redirectAttributes.addFlashAttribute("message",
                    "Commande " + order.getOrderNumber() + " réajustée par la DBI.");
            return "redirect:/orders/{id}";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/orders/{id}";
        }
    }

    @PostMapping("/{id}/validate-dbi")
    public String validateByDbi(@PathVariable Long id,
                                @AuthenticationPrincipal CustomUserDetails currentUser,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        return executeOrderAction(id, currentUser, request, redirectAttributes, "VALIDATION_DBI",
                "Validation DBI de la commande %s (engagement fournisseur enregistré)",
                () -> orderService.validateByDbi(id, currentUser.getId(), currentUser.getRole()),
                order -> "Commande " + order.getOrderNumber() + " validée par la DBI : engagement fournisseur de "
                        + order.getTotalAmount() + " enregistré.");
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id,
                         @Valid @ModelAttribute("rejectionForm") OrderRejectionFormDto form,
                         BindingResult result,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         HttpServletRequest request,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return backToDetailWithOrder(id, currentUser, model);
        }
        try {
            OrderDto order = orderService.rejectOrder(id, form.getRejectionReason(),
                    currentUser.getId(), currentUser.getRole());
            auditService.record(currentUser.getId(), "REJET", "ORDERS", id,
                    "Rejet de la commande " + order.getOrderNumber() + " par la DBI",
                    clientIpResolver.resolve(request));
            redirectAttributes.addFlashAttribute("message",
                    "Commande " + order.getOrderNumber() + " rejetée.");
            return "redirect:/orders/{id}";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/orders/{id}";
        }
    }

    @PostMapping("/{id}/send")
    public String send(@PathVariable Long id,
                       @AuthenticationPrincipal CustomUserDetails currentUser,
                       HttpServletRequest request,
                       RedirectAttributes redirectAttributes) {
        return executeOrderAction(id, currentUser, request, redirectAttributes, "ENVOI_FOURNISSEUR",
                "Transmission de la commande %s au fournisseur",
                () -> orderService.sendToSupplier(id, currentUser.getId(), currentUser.getRole()),
                order -> "Commande " + order.getOrderNumber() + " transmise au fournisseur.");
    }

    @PostMapping("/{id}/receive")
    public String receive(@PathVariable Long id,
                          @Valid @ModelAttribute("receptionForm") OrderReceptionFormDto form,
                          BindingResult result,
                          @AuthenticationPrincipal CustomUserDetails currentUser,
                          HttpServletRequest request,
                          Model model,
                          RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return backToDetailWithOrder(id, currentUser, model);
        }
        try {
            OrderDto order = orderService.receiveOrder(id, form, currentUser.getId(),
                    currentUser.getRole(), currentUser.getEntityId());
            auditService.record(currentUser.getId(), "RECEPTION", "ORDERS", id,
                    "Réception de la commande " + order.getOrderNumber() + " au Dépôt Central (stock crédité)",
                    clientIpResolver.resolve(request));
            redirectAttributes.addFlashAttribute("message",
                    "Réception enregistrée : " + order.getOrderNumber() + " — " + order.getStatusLabel() + ".");
            return "redirect:/orders/{id}";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/orders/{id}";
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface OrderAction {
        OrderDto run();
    }

    @FunctionalInterface
    private interface FlashMessage {
        String apply(OrderDto order);
    }

    /**
     * Exécute une action du circuit : succès -> audit + flash message,
     * erreur métier -> flash message d'erreur sur la page de détail.
     */
    private String executeOrderAction(Long id, CustomUserDetails currentUser, HttpServletRequest request,
                                      RedirectAttributes redirectAttributes, String actionType,
                                      String auditTemplate, OrderAction action, FlashMessage successMessage) {
        try {
            OrderDto order = action.run();
            auditService.record(currentUser.getId(), actionType, "ORDERS", id,
                    String.format(auditTemplate, order.getOrderNumber()), clientIpResolver.resolve(request));
            redirectAttributes.addFlashAttribute("message", successMessage.apply(order));
            return "redirect:/orders/{id}";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/orders/{id}";
        }
    }

    private String backToDetailWithOrder(Long id, CustomUserDetails currentUser, Model model) {
        OrderDto order = orderService.findOrderDetail(id,
                currentUser.getEntityId(), currentUser.getRole())
                .orElseThrow(() -> new BusinessException("Commande introuvable : id=" + id));
        model.addAttribute("order", order);
        model.addAttribute("adjustmentForm", new OrderAdjustmentFormDto());
        model.addAttribute("rejectionForm", new OrderRejectionFormDto());
        model.addAttribute("receptionForm", new OrderReceptionFormDto());
        addOrderActions(order, currentUser, model);
        return "pages/orders/detail";
    }

    /**
     * Demandes d'approvisionnement DC → DAAS ouvertes (traitables par une commande).
     */
    private List<SupplyRequestDto> openSupplyRequests(CustomUserDetails currentUser) {
        if (!List.of("DAAS", "ADMIN").contains(currentUser.getRole())) {
            return List.of();
        }
        return supplyRequestService.findOpenRequestsForDaas();
    }

    /**
     * Indicateurs d'actions contextuels (statut + rôle) pour la vue de détail.
     */
    private void addOrderActions(OrderDto order, CustomUserDetails currentUser, Model model) {
        String role = currentUser.getRole();
        String status = order.getStatus();
        boolean isDbi = "DBI".equals(role) || "ADMIN".equals(role);
        boolean isDaas = "DAAS".equals(role) || "ADMIN".equals(role);
        boolean isDc = "RESPONSABLE_DEPOT".equals(role) || "MAGASINIER".equals(role) || "ADMIN".equals(role);

        model.addAttribute("canSubmit", "BROUILLON".equals(status) && isDaas);
        model.addAttribute("canAdjust",
                ("EN_ATTENTE_DBI".equals(status) || "AJUSTEE_DBI".equals(status)) && isDbi);
        model.addAttribute("canValidateDbi",
                ("EN_ATTENTE_DBI".equals(status) || "AJUSTEE_DBI".equals(status)) && isDbi);
        model.addAttribute("canReject",
                ("EN_ATTENTE_DBI".equals(status) || "AJUSTEE_DBI".equals(status)) && isDbi);
        model.addAttribute("canSend", "VALIDEE_DBI".equals(status) && isDaas);
        model.addAttribute("canReceive",
                ("ENVOYEE_FOURNISSEUR".equals(status) || "RECUE_PARTIELLEMENT".equals(status)) && isDc
                        && ("ADMIN".equals(role)
                            || order.getReceivingEntityId().equals(currentUser.getEntityId())));
    }
}
