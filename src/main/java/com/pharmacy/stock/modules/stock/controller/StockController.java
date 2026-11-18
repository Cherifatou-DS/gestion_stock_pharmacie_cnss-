package com.pharmacy.stock.modules.stock.controller;

import com.pharmacy.stock.common.exception.BusinessException;
import com.pharmacy.stock.common.util.ClientIpResolver;
import com.pharmacy.stock.modules.audit.service.AuditService;
import com.pharmacy.stock.modules.entity.service.StructureEntityService;
import com.pharmacy.stock.modules.product.service.ProductService;
import com.pharmacy.stock.modules.stock.dto.StockMovementFormDto;
import com.pharmacy.stock.modules.stock.dto.StockThresholdFormDto;
import com.pharmacy.stock.modules.stock.entity.StockMovementType;
import com.pharmacy.stock.modules.stock.service.StockService;
import com.pharmacy.stock.modules.user.security.CustomUserDetails;
import com.pharmacy.stock.modules.user.security.RoleNames;
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
 * Gestion des stocks & inventaires — routes : /stocks
 */
@Controller
@RequestMapping("/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final StructureEntityService structureEntityService;
    private final ProductService productService;
    private final AuditService auditService;
    private final ClientIpResolver clientIpResolver;

    @GetMapping
    public String list(@RequestParam(required = false) Long entityId,
                       @RequestParam(required = false, defaultValue = "false") boolean lowStockOnly,
                       @AuthenticationPrincipal CustomUserDetails currentUser,
                       Model model) {
        // Les utilisateurs hors administration sont limités à la consultation de leur structure.
        boolean restricted = RoleNames.isRestrictedToOwnEntity(currentUser.getRole());
        Long effectiveEntityId = restricted ? currentUser.getEntityId() : entityId;

        model.addAttribute("stocks", stockService.findStocks(effectiveEntityId, lowStockOnly));
        model.addAttribute("entities", restricted ? List.of() : structureEntityService.findAll());
        model.addAttribute("selectedEntityId", effectiveEntityId);
        model.addAttribute("lowStockOnly", lowStockOnly);
        model.addAttribute("restricted", restricted);
        model.addAttribute("lowStockCount", stockService.findLowStocks().size());
        return "pages/stocks/list";
    }

    @GetMapping("/{id}/threshold")
    public String thresholdForm(@PathVariable Long id, Model model) {
        var stock = stockService.findStockById(id)
                .orElseThrow(() -> new BusinessException("Ligne de stock introuvable : id=" + id));
        StockThresholdFormDto form = new StockThresholdFormDto();
        form.setMinThreshold(stock.getMinThreshold());
        model.addAttribute("stock", stock);
        model.addAttribute("thresholdForm", form);
        return "pages/stocks/threshold-form";
    }

    @PostMapping("/{id}/threshold")
    public String updateThreshold(@PathVariable Long id,
                                  @Valid @ModelAttribute("thresholdForm") StockThresholdFormDto form,
                                  BindingResult result,
                                  Model model,
                                  @AuthenticationPrincipal CustomUserDetails currentUser,
                                  HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
        var stock = stockService.findStockById(id)
                .orElseThrow(() -> new BusinessException("Ligne de stock introuvable : id=" + id));
        if (result.hasErrors()) {
            model.addAttribute("stock", stock);
            return "pages/stocks/threshold-form";
        }
        try {
            var updated = stockService.updateThreshold(id, form, currentUser.getRole());
            auditService.record(currentUser.getId(), "MODIFICATION", "STOCKS", id,
                    "Mise à jour du seuil d'alerte du stock " + stock.getEntityCode() + " / "
                            + stock.getProductCode() + " : " + stock.getMinThreshold() + " -> "
                            + updated.getMinThreshold(),
                    clientIpResolver.resolve(request));
            redirectAttributes.addFlashAttribute("message",
                    "Seuil d'alerte mis à jour pour le produit " + stock.getProductCode() + ".");
            return "redirect:/stocks";
        } catch (BusinessException e) {
            model.addAttribute("stock", stock);
            model.addAttribute("error", e.getMessage());
            return "pages/stocks/threshold-form";
        }
    }

    @GetMapping("/movements")
    public String movements(@RequestParam(required = false) Long entityId,
                            @RequestParam(required = false) Long productId,
                            @RequestParam(required = false) String type,
                            @AuthenticationPrincipal CustomUserDetails currentUser,
                            Model model) {
        boolean restricted = RoleNames.isRestrictedToOwnEntity(currentUser.getRole());
        Long effectiveEntityId = restricted ? currentUser.getEntityId() : entityId;

        model.addAttribute("movements", stockService.findMovements(effectiveEntityId, productId, type));
        model.addAttribute("entities", restricted ? List.of() : structureEntityService.findAll());
        model.addAttribute("products", productService.findAll());
        model.addAttribute("movementTypes", Arrays.asList(StockMovementType.values()));
        model.addAttribute("selectedEntityId", effectiveEntityId);
        model.addAttribute("selectedProductId", productId);
        model.addAttribute("selectedType", type);
        model.addAttribute("restricted", restricted);
        return "pages/stocks/movements";
    }

    @GetMapping("/movements/new")
    public String movementForm(@AuthenticationPrincipal CustomUserDetails currentUser,
                               @RequestParam(required = false) Long stockId,
                               Model model) {
        StockMovementFormDto form = new StockMovementFormDto();
        Long defaultEntityId = currentUser.getEntityId();
        if (stockId != null) {
            defaultEntityId = stockService.findStockById(stockId)
                    .map(com.pharmacy.stock.modules.stock.dto.StockDto::getEntityId)
                    .orElse(defaultEntityId);
        }
        form.setEntityId(defaultEntityId);
        model.addAttribute("movementForm", form);
        model.addAttribute("entities", structureEntityService.findAll());
        model.addAttribute("products", productService.findAll());
        model.addAttribute("movementTypes", Arrays.asList(StockMovementType.values()));
        return "pages/stocks/movement-form";
    }

    @PostMapping("/movements")
    public String createMovement(@Valid @ModelAttribute("movementForm") StockMovementFormDto form,
                                 BindingResult result,
                                 Model model,
                                 @AuthenticationPrincipal CustomUserDetails currentUser,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("entities", structureEntityService.findAll());
            model.addAttribute("products", productService.findAll());
            model.addAttribute("movementTypes", Arrays.asList(StockMovementType.values()));
            return "pages/stocks/movement-form";
        }
        try {
            var movement = stockService.recordUserMovement(form, currentUser.getId(),
                    currentUser.getRole(), currentUser.getEntityId());
            auditService.record(currentUser.getId(), "MOUVEMENT_STOCK", "STOCK_MOVEMENTS", movement.getId(),
                    movement.getType() + " de " + movement.getQuantity() + " " + movement.getProductCode()
                            + " sur la structure " + movement.getEntityCode()
                            + " (stock : " + movement.getStockBefore() + " -> " + movement.getStockAfter() + ")",
                    clientIpResolver.resolve(request));
            redirectAttributes.addFlashAttribute("message",
                    "Mouvement enregistré : stock de " + movement.getProductCode() + " passé de "
                            + movement.getStockBefore() + " à " + movement.getStockAfter() + ".");
            return "redirect:/stocks/movements";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("entities", structureEntityService.findAll());
            model.addAttribute("products", productService.findAll());
            model.addAttribute("movementTypes", Arrays.asList(StockMovementType.values()));
            return "pages/stocks/movement-form";
        }
    }

    /**
     * Les rôles centres médicaux ne consultent que le stock de leur propre structure.
     * (Règle centralisée dans {@link RoleNames} ; contrôle d'écriture renforcé
     * côté service via recordUserMovement / updateThreshold.)
     */
}
