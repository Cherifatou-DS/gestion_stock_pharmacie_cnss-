package com.pharmacy.stock.modules.order.service;

import com.pharmacy.stock.common.exception.BusinessException;
import com.pharmacy.stock.modules.entity.entity.StructureEntity;
import com.pharmacy.stock.modules.entity.repository.StructureEntityRepository;
import com.pharmacy.stock.modules.order.dto.OrderAdjustmentFormDto;
import com.pharmacy.stock.modules.order.dto.OrderDto;
import com.pharmacy.stock.modules.order.dto.OrderFormDto;
import com.pharmacy.stock.modules.order.dto.OrderItemDto;
import com.pharmacy.stock.modules.order.dto.OrderLineFormDto;
import com.pharmacy.stock.modules.order.entity.Order;
import com.pharmacy.stock.modules.order.entity.OrderItem;
import com.pharmacy.stock.modules.order.entity.OrderStatus;
import com.pharmacy.stock.modules.order.repository.OrderRepository;
import com.pharmacy.stock.modules.product.entity.Product;
import com.pharmacy.stock.modules.product.repository.ProductRepository;
import com.pharmacy.stock.modules.stock.dto.StockMovementFormDto;
import com.pharmacy.stock.modules.stock.repository.StockRepository;
import com.pharmacy.stock.modules.stock.service.StockService;
import com.pharmacy.stock.modules.user.entity.User;
import com.pharmacy.stock.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_RESPONSABLE_PHARMACIE = "RESPONSABLE_PHARMACIE";
    private static final String ROLE_RESPONSABLE_DEPOT = "RESPONSABLE_DEPOT";
    private static final String ROLE_DBI = "DBI";
    private static final String ROLE_CONSULTATION_CM = "CHEF_SERVICE";

    private static final String TYPE_CM = "CENTRE_MEDICAL";
    private static final String TYPE_PC = "PHARMACIE_CENTRALE";
    private static final String TYPE_DC = "DEPOT_CENTRAL";
    private static final String TYPE_FOURNISSEUR = "FOURNISSEUR";

    private static final String MOUVEMENT_ENTREE = "ENTREE";
    private static final String MOUVEMENT_SORTIE = "SORTIE";

    private final OrderRepository orderRepository;
    private final StructureEntityRepository structureEntityRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final StockService stockService;

    // ------------------------------------------------------------------
    // Création & soumission
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public OrderDto createOrder(OrderFormDto form, Long userId, String userRole, Long userEntityId) {
        StructureEntity source = structureEntityRepository.findById(userEntityId)
                .orElseThrow(() -> new BusinessException("Structure de rattachement introuvable : id=" + userEntityId));
        StructureEntity target = structureEntityRepository.findById(form.getTargetEntityId())
                .orElseThrow(() -> new BusinessException("Structure destinataire introuvable : id=" + form.getTargetEntityId()));

        validateTargetAndRole(source, target, userRole);

        List<OrderLineFormDto> lines = sanitizeLines(form.getLines());
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Utilisateur introuvable : id=" + userId));

        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setSourceEntity(source);
        order.setTargetEntity(target);
        order.setStatus(OrderStatus.BROUILLON.name());
        order.setRequiresDbiValidation(shouldRequireDbiValidation(target));
        order.setCreatedByUser(author);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        for (OrderLineFormDto line : lines) {
            Product product = productRepository.findById(line.getProductId())
                    .orElseThrow(() -> new BusinessException("Produit introuvable : id=" + line.getProductId()));
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProduct(product);
            item.setQuantityRequested(line.getQuantityRequested());
            item.setQuantityDelivered(0);
            order.getOrderItems().add(item);
        }

        return toDto(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderDto submitOrder(Long orderId, Long userId, String userRole) {
        Order order = requireOrder(orderId);
        requireDraftOwner(order, userId, userRole);
        requireStatus(order, OrderStatus.BROUILLON);

        order.setStatus(shouldRequireDbiValidation(order.getTargetEntity())
                ? OrderStatus.EN_ATTENTE_DBI.name()
                : OrderStatus.EN_ATTENTE_SERVIE.name());
        order.setUpdatedAt(LocalDateTime.now());
        return toDto(orderRepository.save(order));
    }

    // ------------------------------------------------------------------
    // Processus A : livraison directe par la Pharmacie Centrale
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public OrderDto deliverOrder(Long orderId, Long userId, String userRole) {
        Order order = requireOrder(orderId);
        requireRole(userRole, List.of(ROLE_RESPONSABLE_PHARMACIE, ROLE_ADMIN),
                "Seul le responsable de la Pharmacie Centrale peut délivrer une demande directe.");
        requireStatus(order, OrderStatus.EN_ATTENTE_SERVIE);

        if (order.getRequiresDbiValidation()) {
            throw new BusinessException("Cette commande est soumise à la validation DBI (circuit de réapprovisionnement).");
        }

        // Vérification de la disponibilité avant tout débit (transaction unique = atomicité)
        for (OrderItem item : order.getOrderItems()) {
            int quantity = resolveDeliveryQuantity(item);
            checkStockAvailability(order.getTargetEntity(), item.getProduct(), quantity);
        }

        for (OrderItem item : order.getOrderItems()) {
            int quantity = resolveDeliveryQuantity(item);
            recordStockMovement(order.getTargetEntity(), item.getProduct(), MOUVEMENT_SORTIE, quantity, userId);
            item.setQuantityDelivered(quantity);
        }

        order.setStatus(OrderStatus.LIVREE.name());
        order.setUpdatedAt(LocalDateTime.now());
        return toDto(orderRepository.save(order));
    }

    // ------------------------------------------------------------------
    // Rejet
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public OrderDto rejectOrder(Long orderId, Long userId, String userRole) {
        Order order = requireOrder(orderId);
        OrderStatus status = OrderStatus.fromValue(order.getStatus());
        if (status == OrderStatus.EN_ATTENTE_SERVIE) {
            requireRole(userRole, List.of(ROLE_RESPONSABLE_PHARMACIE, ROLE_ADMIN),
                    "Seul le responsable de la Pharmacie Centrale peut rejeter une demande directe.");
        } else if (status == OrderStatus.EN_ATTENTE_DBI || status == OrderStatus.AJUSTEE_DBI) {
            requireRole(userRole, List.of(ROLE_DBI, ROLE_ADMIN),
                    "Seule la DBI peut rejeter une commande de réapprovisionnement.");
        } else {
            throw new BusinessException("Impossible de rejeter une commande à l'état : " + order.getStatus());
        }

        order.setStatus(OrderStatus.REJETEE.name());
        order.setUpdatedAt(LocalDateTime.now());
        return toDto(orderRepository.save(order));
    }

    // ------------------------------------------------------------------
    // Processus B : circuit DBI
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public OrderDto adjustOrder(Long orderId, OrderAdjustmentFormDto form, Long userId, String userRole) {
        Order order = requireOrder(orderId);
        requireRole(userRole, List.of(ROLE_DBI, ROLE_ADMIN),
                "Seule la DBI peut réajuster les quantités d'une commande.");
        if (!order.getRequiresDbiValidation()) {
            throw new BusinessException("Cette commande n'est pas soumise à la validation DBI.");
        }
        requireStatus(order, OrderStatus.EN_ATTENTE_DBI, OrderStatus.AJUSTEE_DBI);

        for (var adjustment : form.getAdjustments()) {
            order.getOrderItems().stream()
                    .filter(item -> item.getId().equals(adjustment.getItemId()))
                    .findFirst()
                    .ifPresent(item -> {
                        if (adjustment.getAdjustedQuantity() != null
                                && adjustment.getAdjustedQuantity() > item.getQuantityRequested()) {
                            throw new BusinessException("Quantité ajustée supérieure à la quantité demandée pour "
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
        if (!order.getRequiresDbiValidation()) {
            throw new BusinessException("Cette commande n'est pas soumise à la validation DBI.");
        }
        requireStatus(order, OrderStatus.EN_ATTENTE_DBI, OrderStatus.AJUSTEE_DBI);

        order.setStatus(OrderStatus.VALIDEE_DBI.name());
        order.setUpdatedAt(LocalDateTime.now());
        return toDto(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderDto shipOrder(Long orderId, Long userId, String userRole) {
        Order order = requireOrder(orderId);
        requireRole(userRole, List.of(ROLE_RESPONSABLE_DEPOT, ROLE_ADMIN),
                "Seul le responsable du Dépôt Central peut expédier une commande.");
        requireStatus(order, OrderStatus.VALIDEE_DBI);
        if (!TYPE_DC.equals(order.getTargetEntity().getType())) {
            throw new BusinessException("Seules les commandes adressées au Dépôt Central peuvent être expédiées par celui-ci.");
        }

        for (OrderItem item : order.getOrderItems()) {
            int quantity = resolveDeliveryQuantity(item);
            checkStockAvailability(order.getTargetEntity(), item.getProduct(), quantity);
        }

        for (OrderItem item : order.getOrderItems()) {
            int quantity = resolveDeliveryQuantity(item);
            recordStockMovement(order.getTargetEntity(), item.getProduct(), MOUVEMENT_SORTIE, quantity, userId);
            item.setQuantityDelivered(quantity);
        }

        order.setStatus(OrderStatus.EXPEDIEE.name());
        order.setUpdatedAt(LocalDateTime.now());
        return toDto(orderRepository.save(order));
    }

    // ------------------------------------------------------------------
    // Réceptions
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public OrderDto receiveOrder(Long orderId, Long userId, String userRole, Long userEntityId) {
        Order order = requireOrder(orderId);
        requireStatus(order, OrderStatus.EXPEDIEE);
        if (!ROLE_ADMIN.equals(userRole) && !order.getSourceEntity().getId().equals(userEntityId)) {
            throw new BusinessException("Seule la structure émettrice peut réceptionner cette expédition.");
        }

        for (OrderItem item : order.getOrderItems()) {
            int quantity = item.getQuantityDelivered() != null ? item.getQuantityDelivered() : 0;
            if (quantity > 0) {
                recordStockMovement(order.getSourceEntity(), item.getProduct(), MOUVEMENT_ENTREE, quantity, userId);
            }
        }

        order.setStatus(OrderStatus.LIVREE.name());
        order.setUpdatedAt(LocalDateTime.now());
        return toDto(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderDto receiveSupplierDelivery(Long orderId, Long userId, String userRole) {
        Order order = requireOrder(orderId);
        requireRole(userRole, List.of(ROLE_RESPONSABLE_DEPOT, ROLE_ADMIN),
                "Seul le responsable du Dépôt Central peut réceptionner une livraison fournisseur.");
        requireStatus(order, OrderStatus.VALIDEE_DBI);
        if (!TYPE_FOURNISSEUR.equals(order.getTargetEntity().getType())) {
            throw new BusinessException("Cette commande n'est pas une commande Fournisseur.");
        }

        for (OrderItem item : order.getOrderItems()) {
            int quantity = resolveDeliveryQuantity(item);
            recordStockMovement(order.getSourceEntity(), item.getProduct(), MOUVEMENT_ENTREE, quantity, userId);
            item.setQuantityDelivered(quantity);
        }

        order.setStatus(OrderStatus.LIVREE.name());
        order.setUpdatedAt(LocalDateTime.now());
        return toDto(orderRepository.save(order));
    }

    // ------------------------------------------------------------------
    // Consultation
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<OrderDto> findOrders(Long userEntityId, String userRole, Long sourceEntityId, String status) {
        Long filter = isRestrictedToOwnOrders(userRole) ? userEntityId : sourceEntityId;
        return orderRepository.search(filter, OrderStatus.fromValue(status) == null ? null : OrderStatus.fromValue(status).name())
                .stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderDto> findOrderDetail(Long orderId, Long userEntityId, String userRole) {
        return orderRepository.findByIdWithDetails(orderId)
                .filter(order -> !isRestrictedToOwnOrders(userRole)
                        || order.getSourceEntity().getId().equals(userEntityId))
                .map(this::toDto);
    }

    // ------------------------------------------------------------------
    // Règles internes
    // ------------------------------------------------------------------

    private void validateTargetAndRole(StructureEntity source, StructureEntity target, String userRole) {
        String targetType = target.getType();
        boolean allowed;
        switch (userRole == null ? "" : userRole) {
            case ROLE_CONSULTATION_CM, "AGENT_APPROVISIONNEMENT" ->
                allowed = TYPE_PC.equals(target.getType());
            case ROLE_RESPONSABLE_PHARMACIE ->
                allowed = TYPE_DC.equals(target.getType()) || TYPE_PC.equals(target.getType());
            case ROLE_RESPONSABLE_DEPOT ->
                allowed = TYPE_FOURNISSEUR.equals(target.getType()) || TYPE_DC.equals(target.getType());
            case ROLE_DBI, ROLE_ADMIN ->
                allowed = List.of(TYPE_PC, TYPE_DC, TYPE_FOURNISSEUR).contains(target.getType());
            default -> allowed = false;
        }
        if (source.getId().equals(target.getId())) {
            throw new BusinessException("La structure émettrice et la structure destinataire doivent être différentes.");
        }
        if (!allowed) {
            throw new BusinessException("Vous n'êtes pas autorisé à créer une commande vers " + target.getName() + ".");
        }
    }

    private boolean shouldRequireDbiValidation(StructureEntity target) {
        // Circuit direct CM -> PC sans DBI ; tout réapprovisionnement (PC -> DC, DC -> Fournisseur)
        // passe obligatoirement par la validation DBI (Processus B).
        return !TYPE_PC.equals(target.getType());
    }

    private List<OrderLineFormDto> sanitizeLines(List<OrderLineFormDto> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException("La commande doit contenir au moins une ligne de produit.");
        }
        Set<Long> seen = new HashSet<>();
        List<OrderLineFormDto> cleaned = new ArrayList<>();
        for (OrderLineFormDto line : lines) {
            if (line == null || line.getProductId() == null || line.getQuantityRequested() == null
                    || line.getQuantityRequested() < 1) {
                throw new BusinessException("Chaque ligne doit désigner un produit avec une quantité d'au moins 1.");
            }
            if (!seen.add(line.getProductId())) {
                throw new BusinessException("Le produit id=" + line.getProductId() + " figure deux fois dans la commande.");
            }
            cleaned.add(line);
        }
        return cleaned;
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

    private int resolveDeliveryQuantity(OrderItem item) {
        return item.getQuantityAdjusted() != null ? item.getQuantityAdjusted() : item.getQuantityRequested();
    }

    private void checkStockAvailability(StructureEntity entity, Product product, int quantity) {
        int available = stockRepository.findByEntityIdAndProductId(entity.getId(), product.getId())
                .map(stock -> stock.getQuantity() != null ? stock.getQuantity() : 0)
                .orElse(0);
        if (quantity > available) {
            throw new BusinessException("Stock insuffisant de " + entity.getCode() + " pour " + product.getDesignation()
                    + " : " + available + " " + product.getUnit() + " disponible(s), " + quantity + " requis.");
        }
    }

    private void recordStockMovement(StructureEntity entity, Product product, String type, int quantity, Long userId) {
        stockService.recordMovement(new StockMovementFormDto(entity.getId(), product.getId(), type, quantity), userId);
    }

    private Order requireOrder(Long orderId) {
        return orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new BusinessException("Commande introuvable : id=" + orderId));
    }

    private void requireDraftOwner(Order order, Long userId, String userRole) {
        if (!ROLE_ADMIN.equals(userRole) && !order.getCreatedByUser().getId().equals(userId)) {
            throw new BusinessException("Seul l'auteur de la commande peut la soumettre.");
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
        throw new BusinessException("Action non permise : la commande est à l'état " + order.getStatus() + ".");
    }

    private boolean isRestrictedToOwnOrders(String userRole) {
        return !ROLE_ADMIN.equals(userRole)
                && !ROLE_RESPONSABLE_PHARMACIE.equals(userRole)
                && !ROLE_RESPONSABLE_DEPOT.equals(userRole)
                && !ROLE_DBI.equals(userRole);
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
                        item.getQuantityDelivered()))
                .toList();
        return new OrderDto(
                order.getId(),
                order.getOrderNumber(),
                order.getSourceEntity().getId(),
                order.getSourceEntity().getCode(),
                order.getSourceEntity().getName(),
                order.getTargetEntity().getId(),
                order.getTargetEntity().getCode(),
                order.getTargetEntity().getName(),
                order.getTargetEntity().getType(),
                order.getStatus(),
                order.getRequiresDbiValidation(),
                formatUserName(order),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items);
    }

    private String formatUserName(Order order) {
        User user = order.getCreatedByUser();
        if (user == null) {
            return null;
        }
        if (user.getFirstName() == null && user.getLastName() == null) {
            return user.getUsername();
        }
        return ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                + (user.getLastName() != null ? user.getLastName() : "")).trim();
    }
}
