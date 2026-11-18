package com.pharmacy.stock.modules.user.controller;

import com.pharmacy.stock.modules.audit.service.AuditService;
import com.pharmacy.stock.modules.order.repository.OrderRepository;
import com.pharmacy.stock.modules.order.service.OrderService;
import com.pharmacy.stock.modules.product.service.ProductService;
import com.pharmacy.stock.modules.stock.service.StockService;
import com.pharmacy.stock.modules.supply.repository.SupplyRequestRepository;
import com.pharmacy.stock.modules.user.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    /** Commandes fournisseurs en attente de décision DBI. */
    private static final List<String> STATUTS_COMMANDES_EN_ATTENTE =
            List.of("EN_ATTENTE_DBI", "AJUSTEE_DBI");

    /** Demandes d'approvisionnement à traiter par les entités sollicitées. */
    private static final List<String> STATUTS_DEMANDES_A_TRAITER =
            List.of("SOUMISE", "EN_COURS_TRAITEMENT");

    private final ProductService productService;
    private final StockService stockService;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final SupplyRequestRepository supplyRequestRepository;
    private final AuditService auditService;

    @GetMapping({"/", "/dashboard"})
    public String dashboard(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        model.addAttribute("productCount", productService.countAll());
        model.addAttribute("lowStocks", stockService.findLowStocks());
        model.addAttribute("stockLines", stockService.countStocks());
        model.addAttribute("pendingOrders", orderRepository.countByStatusIn(STATUTS_COMMANDES_EN_ATTENTE));
        model.addAttribute("readyToSendOrders", orderRepository.countByStatus("VALIDEE_DBI"));
        model.addAttribute("pendingSupplyRequests",
                supplyRequestRepository.countByStatusIn(STATUTS_DEMANDES_A_TRAITER));
        model.addAttribute("recentOrders",
                orderService.findOrders(currentUser.getEntityId(), currentUser.getRole(), null, null)
                        .stream().limit(5).toList());
        model.addAttribute("recentLogs", auditService.findRecent(5));
        return "pages/dashboard/index";
    }
}
