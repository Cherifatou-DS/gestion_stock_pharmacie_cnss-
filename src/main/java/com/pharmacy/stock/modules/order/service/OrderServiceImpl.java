package com.pharmacy.stock.modules.order.service;

import com.pharmacy.stock.common.exception.BusinessException;
import com.pharmacy.stock.modules.entity.entity.StructureEntity;
import com.pharmacy.stock.modules.entity.repository.StructureEntityRepository;
import com.pharmacy.stock.modules.finance.service.FinanceService;
import com.pharmacy.stock.modules.order.dto.OrderAdjustmentFormDto;
import com.pharmacy.stock.modules.order.dto.OrderDto;
import com.pharmacy.stock.modules.order.dto.OrderFormDto;
import com.pharmacy.stock.modules.order.dto.OrderItemDto;
import com.pharmacy.stock.modules.order.dto.OrderLineFormDto;
import com.pharmacy.stock.modules.order.dto.OrderReceptionFormDto;
import com.pharmacy.stock.modules.order.dto.OrderReceptionLineDto;
import com.pharmacy.stock.modules.order.entity.Order;
import com.pharmacy.stock.modules.order.entity.OrderItem;
import com.pharmacy.stock.modules.order.entity.OrderStatus;
import com.pharmacy.stock.modules.order.repository.OrderRepository;
import com.pharmacy.stock.modules.product.entity.Product;
import com.pharmacy.stock.modules.product.repository.ProductRepository;
import com.pharmacy.stock.modules.stock.dto.StockMovementFormDto;
import com.pharmacy.stock.modules.stock.repository.StockRepository;
import com.pharmacy.stock.modules.stock.service.StockService;
import com.pharmacy.stock.modules.supply.entity.SupplyRequest;
import com.pharmacy.stock.modules.supply.entity.SupplyRequestStatus;
import com.pharmacy.stock.modules.supply.repository.SupplyRequestRepository;
import com.pharmacy.stock.modules.supply.service.SupplyRequestService;
import com.pharmacy.stock.modules.user.entity.User;
import com.pharmacy.stock.modules.user.repository.UserRepository;
import com.pharmacy.stock.modules.user.security.RoleNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Circuit des commandes fournisseurs (spécification v1.2) :
 * DAAS (création, BROUILLON) → soumission → EN_ATTENTE_DBI → DBI examine
 * (ajustement AJUSTEE_DBI / validation VALIDEE_DBI / rejet REJETEE) →
 * transmission fournisseur ENVOYEE_FOURNISSEUR → réception au Dépôt Central
 * (RECUE_PARTIELLEMENT ou RECUE) avec mouvements ENTREE au DC et engagement
 * financier enregistré par la DBI à la validation.
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String ROLE_ADMIN = RoleNames.ADMIN;
    private static final String ROLE_RESPONSABLE_DEPOT = RoleNames.RESPONSABLE_DEPOT;
    private static final String ROLE_MAGASINIER = RoleNames.MAGASINIER;
    private static final String ROLE_DBI = "DBI";
    private static final String ROLE_DAAS = "DAAS";

    private static final String TYPE_DC = "DEPOT_CENTRAL";
    private static final String TYPE_DAAS = "DAAS";
    private static final String TYPE_FOURNISSEUR = "FOURNISSEUR";

    private static final String MOUVEMENT_ENTREE = "ENTREE";

    private final OrderRepository orderRepository;
    private final StructureEntityRepository structureEntityRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final StockService stockService;
    private final FinanceService financeService;
    private final SupplyRequestService supplyRequestService;
    private final SupplyRequestRepository supplyRequestRepository;

    // ------------------------------------------------------------------
    // Création & soumission (DAAS)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public OrderDto createOrder(OrderFormDto form, Long userId, String userRole, Long userEntityId) {
        requireRole(userRole, List.of(ROLE_DAAS, ROLE_ADMIN),
                "Seule la DAAS peut créer une commande fournisseur.");
        StructureEntity source = structureEntityRepository.findById(userEntityId)
                .orElseThrow(() -> new BusinessException(
                        "Structure de rattachement introuvable : id=" + userEntityId));
        if (!TYPE_DAAS.equals(source.getType()) && !ROLE_ADMIN.equals(userRole)) {
            throw new BusinessException("Seul un utilisateur de la DAAS peut créer une commande fournisseur.");
        }

        StructureEntity supplier = structureEntityRepository.findById(form.getSupplierEntityId())
                .orElseThrow(() -> new BusinessException(
                        "Fournisseur introuvable : id=" + form.getSupplierEntityId()));
        if (!TYPE_FOURNISSEUR.equals(supplier.getType())) {
            throw new BusinessException(
                    "Une commande fournisseur doit être adressée à un fournisseur (type FOURNISSEUR).");
        }
        StructureEntity receiving = structureEntityRepository.findByType(TYPE_DC).stream().findFirst()
                .orElseThrow(() -> new BusinessException("Aucun Dépôt Central de référence en base."));

        User author = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Utilisateur introuvable : id=" + userId));

        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setSourceEntity(source);
        order.setSupplierEntity(supplier);
        order.setReceivingEntity(receiving);
        order.setStatus(OrderStatus.BROUILLON.name());
        order.setTotalAmount(BigDecimal.ZERO);
        order.setCreatedByUser(author);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        List<OrderLineFormDto> lines = sanitizeLines(form.getLines(), form.getSupplyRequestId());
        if (lines.isEmpty()) {
            lines = linesFromSupplyRequest(form.getSupplyRequestId());
        }
        for (OrderLineFormDto line : lines) {
            Product product = productRepository.findById(line.getProductId())
                    .orElseThrow(() -> new BusinessException("Produit introuvable : id=" + line.getProductId()));
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProduct(product);
            item.setQuantityRequested(line.getQuantityRequested());
            item.setQuantityReceived(0);
            item.setUnitPrice(product.getUnitPrice() != null ? product.getUnitPrice() : BigDecimal.ZERO);
            order.getOrderItems().add(item);
        }

        if (form.getSupplyRequestId() != null) {
            SupplyRequest request = requireSupplyRequest(form.getSupplyRequestId());
            order.setSupplyRequest(request);
        }

        Order saved = orderRepository.save(order);
        if (order.getSupplyRequest() != null) {
            supplyRequestService.markProcessingByDaas(order.getSupplyRequest().getId(), userId);
        }
        return toDto(saved);
    }

    @Override
    @Transactional
    public OrderDto submitOrder(Long orderId, Long userId, String userRole) {
        Order order = requireOrder(orderId);
        requireRole(userRole, List.of(ROLE_DAAS, ROLE_ADMIN),
                "Seule la DAAS peut soumettre une commande fournisseur.");
        requireAuthor(order, userId, userRole);
        requireStatus(order, OrderStatus.BROUILLON);

        order.setStatus(OrderStatus.EN_ATTENTE_DBI.name());
        order.setUpdatedAt(LocalDateTime.now());
        return toDto(orderRepository.save(order));
    }

    // ------------------------------------------------------------------
    // Décisions DBI
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public OrderDto adjustOrder(Long orderId, OrderAdjustmentFormDto form, Long userId, String userRole) {
        Order order = requireOrder(orderId);
        requireRole(userRole, List.of(ROLE_DBI, ROLE_ADMIN),
                "Seule la DBI peut réajuster les quantités d'une commande.");
        requireStatus(order, OrderStatus.EN_ATTENTE_DBI, OrderStatus.AJUSTEE_DBI);
        if (form == null || form.getAdjustments() == null) {
            throw new BusinessException("Le formulaire de réajustement est incomplet.");
        }

        for (var adjustment : form.getAdjustments()) {
            order.getOrderItems().stream()
                    .filter(item -> item.getId().equals(adjustment.getItemId()))
                    .findFirst()
                    .ifPresent(item -> {
                        if (adjustment.getAdjustedQuantity() != null
                                && adjustment.getAdjustedQuantity() > item.getQuantityRequested()) {
                            throw new BusinessException(
                                    "Quantité ajustée supérieure à la quantité demandée pour "
                                            + item.getProduct().getCode() + ".");
                        }
                        item.setQuantityAdjusted(adjustment.getAdjustedQuantity());
                    });
        }

        order.setStatus(OrderStatus.AJUSTEE_DBI.name());
        order.setUpdatedAt(LocalDateTime.now());
        return toDto(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderDto validateByDbi(Long orderId, Long userId, String userRole) {
        Order order = requireOrder(orderId);
        requireRole(userRole, List.of(ROLE_DBI, ROLE_ADMIN), "Seule la DBI peut valider une commande.");
        requireStatus(order, OrderStatus.EN_ATTENTE_DBI, OrderStatus.AJUSTEE_DBI);

        order.setStatus(OrderStatus.VALIDEE_DBI.name());
        order.setValidatedByUser(requireUser(userId));
        order.setValidatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);

        BigDecimal total = computeTotal(saved);
        saved.setTotalAmount(total);
        financeService.recordSupplierEngagement(saved.getId(), total, userId,
                "Engagement fournisseur pour la commande " + saved.getOrderNumber()
                        + " (" + saved.getSupplierEntity().getName() + ")");
        return toDto(orderRepository.save(saved));
    }

    @Override
    @Transactional
    public OrderDto rejectOrder(Long orderId, String rejectionReason, Long userId, String userRole) {
        Order order = requireOrder(orderId);
        requireRole(userRole, List.of(ROLE_DBI, ROLE_ADMIN),
                "Seule la DBI peut rejeter une commande fournisseur.");
        requireStatus(order, OrderStatus.EN_ATTENTE_DBI, OrderStatus.AJUSTEE_DBI);
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new BusinessException("Le motif du rejet est obligatoire.");
        }

        order.setStatus(OrderStatus.REJETEE.name());
        order.setRejectionReason(rejectionReason.trim());
        order.setUpdatedAt(LocalDateTime.now());
        return toDto(orderRepository.save(order));
    }

    // ------------------------------------------------------------------
    // Transmission fournisseur & réception Dépôt Central
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public OrderDto sendToSupplier(Long orderId, Long userId, String userRole) {
        Order order = requireOrder(orderId);
        requireRole(userRole, List.of(ROLE_DAAS, ROLE_ADMIN),
                "Seule la DAAS peut transmettre une commande au fournisseur.");
        requireAuthor(order, userId, userRole);
        requireStatus(order, OrderStatus.VALIDEE_DBI);

        order.setStatus(OrderStatus.ENVOYEE_FOURNISSEUR.name());
        order.setSentAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return toDto(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderDto receiveOrder(Long orderId, OrderReceptionFormDto form, Long userId, String userRole,
                                 Long userEntityId) {
        Order order = requireOrder(orderId);
        requireRole(userRole, List.of(ROLE_RESPONSABLE_DEPOT, ROLE_MAGASINIER, ROLE_ADMIN),
                "Seul le Dépôt Central peut réceptionner une commande fournisseur.");
        requireStatus(order, OrderStatus.ENVOYEE_FOURNISSEUR, OrderStatus.RECUE_PARTIELLEMENT);
        if (!ROLE_ADMIN.equals(userRole) && !order.getReceivingEntity().getId().equals(userEntityId)) {
            throw new BusinessException("Seul le Dépôt Central destinataire peut réceptionner cette commande.");
        }
        if (form == null || form.getReceptions() == null || form.getReceptions().isEmpty()) {
            throw new BusinessException("Les quantités reçues doivent être renseignées.");
        }

        for (OrderReceptionLineDto reception : form.getReceptions()) {
            OrderItem item = requireItem(order, reception.getItemId());
            int alreadyReceived = item.getQuantityReceived() != null ? item.getQuantityReceived() : 0;
            int remaining = item.effectiveQuantity() - alreadyReceived;
            if (reception.getReceivedQuantity() > remaining) {
                throw new BusinessException("Quantité reçue supérieure au reste à recevoir pour "
                        + item.getProduct().getCode() + " (reste : " + remaining + ").");
            }
            if (reception.getReceivedQuantity() > 0) {
                recordStockMovement(order.getReceivingEntity(), item.getProduct(),
                        MOUVEMENT_ENTREE, reception.getReceivedQuantity(), userId, null, order.getId());
                item.setQuantityReceived(alreadyReceived + reception.getReceivedQuantity());
            }
        }

        boolean complete = order.getOrderItems().stream()
                .allMatch(item -> (item.getQuantityReceived() != null ? item.getQuantityReceived() : 0)
                        >= item.effectiveQuantity());
        order.setStatus(complete ? OrderStatus.RECUE.name() : OrderStatus.RECUE_PARTIELLEMENT.name());
        order.setReceivedByUser(requireUser(userId));
        order.setReceivedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);

        if (saved.getSupplyRequest() != null) {
            supplyRequestService.registerOrderDelivery(saved);
        }
        return toDto(saved);
    }

    // ------------------------------------------------------------------
    // Consultation
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<OrderDto> findOrders(Long userEntityId, String userRole, Long supplierEntityId, String status) {
        String statusValue = OrderStatus.fromValue(status) == null ? null : OrderStatus.fromValue(status).name();
        return orderRepository.search(supplierEntityId, statusValue).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderDto> findOrderDetail(Long orderId, Long userEntityId, String userRole) {
        return orderRepository.findByIdWithDetails(orderId).map(this::toDto);
    }

    // ------------------------------------------------------------------
    // Règles internes
    // ------------------------------------------------------------------

    private List<OrderLineFormDto> sanitizeLines(List<OrderLineFormDto> lines, Long supplyRequestId) {
        if (lines == null || lines.isEmpty()) {
            if (supplyRequestId == null) {
                throw new BusinessException("La commande doit contenir au moins une ligne de produit.");
            }
            return new ArrayList<>();
        }
        Set<Long> seen = new HashSet<>();
        List<OrderLineFormDto> cleaned = new ArrayList<>();
        for (OrderLineFormDto line : lines) {
            if (line == null || line.getProductId() == null || line.getQuantityRequested() == null
                    || line.getQuantityRequested() < 1) {
                throw new BusinessException(
                        "Chaque ligne doit désigner un produit avec une quantité d'au moins 1.");
            }
            if (!seen.add(line.getProductId())) {
                throw new BusinessException(
                        "Le produit id=" + line.getProductId() + " figure deux fois dans la commande.");
            }
            cleaned.add(line);
        }
        return cleaned;
    }

    /**
     * Préremplissage des lignes de commande depuis la demande d'approvisionnement
     * DC → DAAS liée.
     */
    private List<OrderLineFormDto> linesFromSupplyRequest(Long supplyRequestId) {
        if (supplyRequestId == null) {
            throw new BusinessException("La commande doit contenir au moins une ligne de produit.");
        }
        SupplyRequest request = requireSupplyRequest(supplyRequestId);
        List<OrderLineFormDto> lines = new ArrayList<>();
        for (var item : request.getRequestItems()) {
            lines.add(new OrderLineFormDto(item.getProduct().getId(), item.getQuantityRequested()));
        }
        if (lines.isEmpty()) {
            throw new BusinessException(
                    "La demande d'approvisionnement liée ne contient aucune ligne de produit.");
        }
        return lines;
    }

    private SupplyRequest requireSupplyRequest(Long supplyRequestId) {
        SupplyRequest request = supplyRequestRepository.findById(supplyRequestId)
                .orElseThrow(() -> new BusinessException(
                        "Demande d'approvisionnement introuvable : id=" + supplyRequestId));
        if (!TYPE_DAAS.equals(request.getSupplyingEntity().getType())
                || !"DEPOT_CENTRAL".equals(request.getRequestingEntity().getType())) {
            throw new BusinessException(
                    "Seule une demande d'approvisionnement DC → DAAS peut donner lieu à une commande fournisseur.");
        }
        SupplyRequestStatus current = SupplyRequestStatus.fromValue(request.getStatus());
        if (current != SupplyRequestStatus.SOUMISE && current != SupplyRequestStatus.EN_COURS_TRAITEMENT) {            throw new BusinessException(
                    "La demande liée n'est plus traitable (état : " + request.getStatus() + ").");
        }
        if (orderRepository.existsBySupplyRequestId(supplyRequestId)) {
            throw new BusinessException(
                    "Une commande fournisseur est déjà liée à la demande " + request.getRequestNumber() + ".");
        }
        return request;
    }

    private void requireAuthor(Order order, Long userId, String userRole) {
        if (!ROLE_ADMIN.equals(userRole) && !order.getCreatedByUser().getId().equals(userId)) {
            throw new BusinessException("Seul l'auteur de la commande peut effectuer cette action.");
        }
    }

    private void requireRole(String userRole, List<String> allowedRoles, String message) {
        if (userRole == null || !allowedRoles.contains(userRole)) {
            throw new BusinessException(message);
        }
    }

    private void requireStatus(Order order, OrderStatus... expected) {
        OrderStatus current = OrderStatus.fromValue(order.getStatus());
        for (OrderStatus candidate : expected) {
            if (candidate == current) {
                return;
            }
        }
        throw new BusinessException("Action non permise : la commande est à l'état "
                + order.getStatus() + ".");
    }

    private OrderItem requireItem(Order order, Long itemId) {
        return order.getOrderItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Ligne de commande introuvable : id=" + itemId));
    }

    private BigDecimal computeTotal(Order order) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : order.getOrderItems()) {
            BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
            total = total.add(unitPrice.multiply(BigDecimal.valueOf(item.effectiveQuantity())));
        }
        return total;
    }

    private void recordStockMovement(StructureEntity entity, Product product, String type, int quantity,
                                     Long userId, Long supplyRequestId, Long orderId) {
        stockService.recordMovement(new StockMovementFormDto(entity.getId(), product.getId(), type, quantity),
                userId, supplyRequestId, orderId);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Utilisateur introuvable : id=" + userId));
    }

    private Order requireOrder(Long orderId) {
        return orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new BusinessException("Commande introuvable : id=" + orderId));
    }

    private String generateOrderNumber() {
        String year = String.valueOf(LocalDateTime.now().getYear());
        String last = orderRepository.findLastOrderNumberOfYear(year);
        int next = 1;
        if (last != null && last.matches("CMD-\\d{4}-\\d{4,}")) {
            next = Integer.parseInt(last.substring(last.lastIndexOf('-') + 1)) + 1;
        }
        return String.format("CMD-%s-%04d", year, next);
    }

    private OrderDto toDto(Order order) {
        List<OrderItemDto> items = order.getOrderItems() == null ? List.of() : order.getOrderItems().stream()
                .map(item -> new OrderItemDto(
                        item.getId(),
                        item.getProduct().getId(),
                        item.getProduct().getCode(),
                        item.getProduct().getDesignation(),
                        item.getProduct().getUnit(),
                        item.getQuantityRequested(),
                        item.getQuantityAdjusted(),
                        item.getQuantityReceived(),
                        item.getUnitPrice()))
                .toList();
        return new OrderDto(
                order.getId(),
                order.getOrderNumber(),
                order.getSourceEntity().getId(),
                order.getSourceEntity().getCode(),
                order.getSourceEntity().getName(),
                order.getSupplierEntity().getId(),
                order.getSupplierEntity().getCode(),
                order.getSupplierEntity().getName(),
                order.getReceivingEntity().getId(),
                order.getReceivingEntity().getCode(),
                order.getReceivingEntity().getName(),
                order.getSupplyRequest() != null ? order.getSupplyRequest().getId() : null,
                order.getSupplyRequest() != null ? order.getSupplyRequest().getRequestNumber() : null,
                order.getStatus(),
                order.getTotalAmount(),
                order.getRejectionReason(),
                formatUserName(order.getCreatedByUser()),
                order.getValidatedByUser() != null ? formatUserName(order.getValidatedByUser()) : null,
                order.getValidatedAt(),
                order.getSentAt(),
                order.getReceivedByUser() != null ? formatUserName(order.getReceivedByUser()) : null,
                order.getReceivedAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items);
    }

    private String formatUserName(User user) {
        if (user.getFirstName() == null && user.getLastName() == null) {
            return user.getUsername();
        }
        return ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                + (user.getLastName() != null ? user.getLastName() : "")).trim();
    }
}
