package com.pharmacy.stock.modules.order.controller;

import com.pharmacy.stock.common.exception.BusinessException;
import com.pharmacy.stock.modules.audit.service.AuditService;
import com.pharmacy.stock.modules.entity.entity.StructureEntity;
import com.pharmacy.stock.modules.entity.service.StructureEntityService;
import com.pharmacy.stock.modules.order.dto.OrderAdjustmentFormDto;
import com.pharmacy.stock.modules.order.dto.OrderDto;
import com.pharmacy.stock.modules.order.dto.OrderFormDto;
import com.pharmacy.stock.modules.order.dto.OrderLineFormDto;
import com.pharmacy.stock.modules.order.entity.OrderStatus;
import com.pharmacy.stock.modules.order.service.OrderService;
import com.pharmacy.stock.modules.product.service.ProductService;
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
 * Circuit des commandes & validations DBI — routes : /orders
 */
@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final StructureEntityService structureEntityService;
    private final ProductService productService;
    private final AuditService auditService;

    @GetMapping
    public String list(@RequestParam(required = false) Long sourceEntityId,
                       @RequestParam(required = false) String status,
                       @AuthenticationPrincipal CustomUserDetails currentUser,
                       Model model) {
        boolean restricted = !isAdminOrManager(currentUser.getRole());
        model.addAttribute("orders", orderService.findOrders(
                currentUser.getEntityId(), currentUser.getRole(), sourceEntityId, status));
        model.addAttribute("entities", restricted ? List.of() : structureEntityService.findAll());
        model.addAttribute("statuses", Arrays.asList(OrderStatus.values()));
        model.addAttribute("selectedSourceEntityId", sourceEntityId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("restricted", restricted);
        return "pages/orders/list";
    }

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        OrderFormDto form = new OrderFormDto();
        form.getLines().add(new OrderLineFormDto());
        model.addAttribute("orderForm", form);
        model.addAttribute("allowedTargets", allowedTargets(currentUser.getRole()));
        model.addAttribute("products", productService.findAll());
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
            model.addAttribute("allowedTargets", allowedTargets(currentUser.getRole()));
            model.addAttribute("products", productService.findAll());
            return "pages/orders/form";
        }
        try {
            OrderDto created = orderService.createOrder(form, currentUser.getId(),
                    currentUser.getRole(), currentUser.getEntityId());
            auditService.record(currentUser.getId(), "CREATION_COMMANDE", "ORDERS", created.getId(),
                    "Création de la commande " + created.getOrderNumber()
                            + " (" + created.getSourceEntityCode() + " -> " + created.getTargetEntityCode()
                            + ", " + created.getItems().size() + " ligne(s))",
                    request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("message",
                    "Commande " + created.getOrderNumber() + " créée à l'état Brouillon.");
            return "redirect:/orders";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("allowedTargets", allowedTargets(currentUser.getRole()));
            model.addAttribute("products", productService.findAll());
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
        addOrderActions(order, currentUser, model);
        return "pages/orders/detail";
    }

    @PostMapping("/{id}/submit")
    public String submit(@PathVariable Long id,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        return executeOrderAction(id, currentUser, request, redirectAttributes, "SOUMISSION",
                "Soumission de la commande %s -> EN_ATTENTE",
                () -> orderService.submitOrder(id, currentUser.getId(), currentUser.getRole()),
                order -> "Commande " + order.getOrderNumber() + " soumise : " + order.getStatusLabel() + ".");
    }

    @PostMapping("/{id}/deliver")
    public String deliver(@PathVariable Long id,
                          @AuthenticationPrincipal CustomUserDetails currentUser,
                          HttpServletRequest request,
                          RedirectAttributes redirectAttributes) {
        return executeOrderAction(id, currentUser, request, redirectAttributes, "LIVRAISON",
                "Livraison directe de la commande %s par la Pharmacie Centrale (stock débité)",
                () -> orderService.deliverOrder(id, currentUser.getId(), currentUser.getRole()),
                order -> "Commande " + order.getOrderNumber() + " délivrée : stock de la PC débité.");
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        return executeOrderAction(id, currentUser, request, redirectAttributes, "REJET",
                "Rejet de la commande %s",
                () -> orderService.rejectOrder(id, currentUser.getId(), currentUser.getRole()),
                order -> "Commande " + order.getOrderNumber() + " rejetée.");
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
                    request.getRemoteAddr());
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
                "Validation DBI de la commande %s",
                () -> orderService.validateByDbi(id, currentUser.getId(), currentUser.getRole()),
                order -> "Commande " + order.getOrderNumber() + " validée par la DBI.");
    }

    @PostMapping("/{id}/ship")
    public String ship(@PathVariable Long id,
                       @AuthenticationPrincipal CustomUserDetails currentUser,
                       HttpServletRequest request,
                       RedirectAttributes redirectAttributes) {
        return executeOrderAction(id, currentUser, request, redirectAttributes, "EXPEDITION",
                "Expédition de la commande %s par le Dépôt Central (stock débité)",
                () -> orderService.shipOrder(id, currentUser.getId(), currentUser.getRole()),
                order -> "Commande " + order.getOrderNumber() + " expédiée : stock du Dépôt Central débité.");
    }

    @PostMapping("/{id}/receive")
    public String receive(@PathVariable Long id,
                          @AuthenticationPrincipal CustomUserDetails currentUser,
                          HttpServletRequest request,
                          RedirectAttributes redirectAttributes) {
        return executeOrderAction(id, currentUser, request, redirectAttributes, "RECEPTION",
                "Réception de la commande %s (stock crédité)",
                () -> orderService.receiveOrder(id, currentUser.getId(),
                        currentUser.getRole(), currentUser.getEntityId()),
                order -> "Commande " + order.getOrderNumber() + " réceptionnée : stock crédité.");
    }

    @PostMapping("/{id}/receive-supplier")
    public String receiveSupplier(@PathVariable Long id,
                                  @AuthenticationPrincipal CustomUserDetails currentUser,
                                  HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
        return executeOrderAction(id, currentUser, request, redirectAttributes, "RECEPTION",
                "Réception fournisseur de la commande %s (stock DC crédité)",
                () -> orderService.receiveSupplierDelivery(id, currentUser.getId(), currentUser.getRole()),
                order -> "Livraison fournisseur de " + order.getOrderNumber() + " réceptionnée : stock du DC crédité.");
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
                    String.format(auditTemplate, order.getOrderNumber()), request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("message", successMessage.apply(order));
            return "redirect:/orders/{id}";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/orders/{id}";
        }
    }

    private String backToDetailWithOrder(Long id, CustomUserDetails currentUser, Model model) {
        model.addAttribute("order", orderService.findOrderDetail(id,
                currentUser.getEntityId(), currentUser.getRole())
                .orElseThrow(() -> new BusinessException("Commande introuvable : id=" + id)));
        return "pages/orders/detail";
    }

    /**
     * Structures destinataires proposées selon le rôle de l'utilisateur.
     */
    private List<StructureEntity> allowedTargets(String role) {
        List<StructureEntity> all = structureEntityService.findAll();
        return switch (role == null ? "" : role) {
            case "CHEF_SERVICE", "AGENT_APPROVISIONNEMENT" ->
                    all.stream().filter(e -> "PHARMACIE_CENTRALE".equals(e.getType())).toList();
            case "RESPONSABLE_PHARMACIE" ->
                    all.stream().filter(e -> "DEPOT_CENTRAL".equals(e.getType())).toList();
            case "RESPONSABLE_DEPOT" ->
                    all.stream().filter(e -> "FOURNISSEUR".equals(e.getType())).toList();
            default ->
                    all.stream().filter(e -> List.of("PHARMACIE_CENTRALE", "DEPOT_CENTRAL", "FOURNISSEUR")
                            .contains(e.getType())).toList();
        };
    }

    private boolean isAdminOrManager(String role) {
        return List.of("ADMIN", "RESPONSABLE_PHARMACIE", "RESPONSABLE_DEPOT", "DBI").contains(role);
    }

    /**
     * Indicateurs d'actions contextuels (statut + rôle) pour la vue de détail.
     */
    private void addOrderActions(OrderDto order, CustomUserDetails currentUser, Model model) {
        String role = currentUser.getRole();
        String status = order.getStatus();
        boolean isDbi = "DBI".equals(role) || "ADMIN".equals(role);
        boolean isPc = "RESPONSABLE_PHARMACIE".equals(role) || "ADMIN".equals(role);
        boolean isDc = "RESPONSABLE_DEPOT".equals(role) || "ADMIN".equals(role);

        model.addAttribute("canSubmit", "BROUILLON".equals(status));
        model.addAttribute("canDeliver", "EN_ATTENTE_SERVIE".equals(status) && isPc);
        model.addAttribute("canReject",
                ("EN_ATTENTE_SERVIE".equals(status) && isPc)
                        || (("EN_ATTENTE_DBI".equals(status) || "AJUSTEE_DBI".equals(status)) && isDbi));
        model.addAttribute("canAdjust", ("EN_ATTENTE_DBI".equals(status) || "AJUSTEE_DBI".equals(status)) && isDbi);
        model.addAttribute("canValidateDbi",
                ("EN_ATTENTE_DBI".equals(status) || "AJUSTEE_DBI".equals(status)) && isDbi);
        model.addAttribute("canShip", "VALIDEE_DBI".equals(status) && isDc
                && "DEPOT_CENTRAL".equals(order.getTargetEntityType()));
        model.addAttribute("canReceiveSupplier",
                "VALIDEE_DBI".equals(status) && isDc && "FOURNISSEUR".equals(order.getTargetEntityType()));
        model.addAttribute("canReceive", "EXPEDIEE".equals(status)
                && ("ADMIN".equals(role) || order.getSourceEntityId().equals(currentUser.getEntityId())));
    }
}
