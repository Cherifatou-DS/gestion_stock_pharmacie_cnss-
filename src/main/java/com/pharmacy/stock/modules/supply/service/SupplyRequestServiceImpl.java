package com.pharmacy.stock.modules.supply.service;

import com.pharmacy.stock.common.exception.BusinessException;
import com.pharmacy.stock.modules.entity.entity.StructureEntity;
import com.pharmacy.stock.modules.entity.repository.StructureEntityRepository;
import com.pharmacy.stock.modules.finance.service.FinanceService;
import com.pharmacy.stock.modules.order.entity.Order;
import com.pharmacy.stock.modules.product.entity.Product;
import com.pharmacy.stock.modules.product.repository.ProductRepository;
import com.pharmacy.stock.modules.stock.dto.StockMovementFormDto;
import com.pharmacy.stock.modules.stock.repository.StockRepository;
import com.pharmacy.stock.modules.stock.service.StockService;
import com.pharmacy.stock.modules.supply.dto.SupplyRequestApprovalLineDto;
import com.pharmacy.stock.modules.supply.dto.SupplyRequestDto;
import com.pharmacy.stock.modules.supply.dto.SupplyRequestFormDto;
import com.pharmacy.stock.modules.supply.dto.SupplyRequestItemDto;
import com.pharmacy.stock.modules.supply.dto.SupplyRequestLineFormDto;
import com.pharmacy.stock.modules.supply.dto.SupplyRequestProcessFormDto;
import com.pharmacy.stock.modules.supply.entity.SupplyRequest;
import com.pharmacy.stock.modules.supply.entity.SupplyRequestItem;
import com.pharmacy.stock.modules.supply.entity.SupplyRequestStatus;
import com.pharmacy.stock.modules.supply.repository.SupplyRequestRepository;
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
 * Circuit des demandes d'approvisionnement internes (CM → PC, PC → DC, DC → DAAS).
 *
 * Règles clés (spécification v1.2) :
 * - les relations autorisées sont strictement CM → PC, PC → DC et DC → DAAS ;
 * - pour CM → PC et PC → DC, l'expédition génère un mouvement SORTIE chez
 *   l'entité fournisseuse et un mouvement ENTREE chez l'entité demandeuse à la
 *   livraison ; la quantité accordée peut être inférieure à la quantité demandée ;
 * - pour DC → DAAS, la demande ne mouvemente pas directement de stock : la DAAS
 *   la traite en émettant une commande fournisseur, et la demande passe à LIVREE
 *   lorsque la réception au Dépôt Central couvre les quantités demandées ;
 * - chaque sortie de stock est valorisée et enregistrée en suivi financier
 *   (transaction SORTIE_STOCK_VALORISEE).
 */
@Service
@RequiredArgsConstructor
public class SupplyRequestServiceImpl implements SupplyRequestService {

    private static final String ROLE_ADMIN = RoleNames.ADMIN;
    private static final String ROLE_RESPONSABLE_PHARMACIE = RoleNames.RESPONSABLE_PHARMACIE;
    private static final String ROLE_RESPONSABLE_DEPOT = RoleNames.RESPONSABLE_DEPOT;
    private static final String ROLE_MAGASINIER = RoleNames.MAGASINIER;
    private static final String ROLE_DAAS = "DAAS";

    private static final String TYPE_CM = "CENTRE_MEDICAL";
    private static final String TYPE_PC = "PHARMACIE_CENTRALE";
    private static final String TYPE_DC = "DEPOT_CENTRAL";
    private static final String TYPE_DAAS = "DAAS";

    private static final String MOUVEMENT_ENTREE = "ENTREE";
    private static final String MOUVEMENT_SORTIE = "SORTIE";

    private final SupplyRequestRepository supplyRequestRepository;
    private final StructureEntityRepository structureEntityRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final StockService stockService;
    private final FinanceService financeService;

    // ------------------------------------------------------------------
    // Création & soumission
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public SupplyRequestDto createRequest(SupplyRequestFormDto form, Long userId,
                                          String userRole, Long userEntityId) {
        StructureEntity requester = structureEntityRepository.findById(userEntityId)
                .orElseThrow(() -> new BusinessException(
                        "Structure de rattachement introuvable : id=" + userEntityId));
        StructureEntity supplier = structureEntityRepository.findById(form.getSupplyingEntityId())
                .orElseThrow(() -> new BusinessException(
                        "Structure sollicitée introuvable : id=" + form.getSupplyingEntityId()));

        validatePairAndRole(requester, supplier, userRole);

        List<SupplyRequestLineFormDto> lines = sanitizeLines(form.getLines());
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Utilisateur introuvable : id=" + userId));

        SupplyRequest request = new SupplyRequest();
        request.setRequestNumber(generateRequestNumber());
        request.setRequestingEntity(requester);
        request.setSupplyingEntity(supplier);
        request.setStatus(SupplyRequestStatus.BROUILLON.name());
        request.setCreatedByUser(author);
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());

        for (SupplyRequestLineFormDto line : lines) {
            Product product = productRepository.findById(line.getProductId())
                    .orElseThrow(() -> new BusinessException("Produit introuvable : id=" + line.getProductId()));
            SupplyRequestItem item = new SupplyRequestItem();
            item.setSupplyRequest(request);
            item.setProduct(product);
            item.setQuantityRequested(line.getQuantityRequested());
            item.setQuantityDelivered(0);
            request.getRequestItems().add(item);
        }

        return toDto(supplyRequestRepository.save(request));
    }

    @Override
    @Transactional
    public SupplyRequestDto submitRequest(Long requestId, Long userId, String userRole) {
        SupplyRequest request = requireRequest(requestId);
        requireDraftOwner(request, userId, userRole);
        requireStatus(request, SupplyRequestStatus.BROUILLON);

        request.setStatus(SupplyRequestStatus.SOUMISE.name());
        request.setUpdatedAt(LocalDateTime.now());
        return toDto(supplyRequestRepository.save(request));
    }

    // ------------------------------------------------------------------
    // Traitement par l'entité sollicitée (PC, DC ou DAAS)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public SupplyRequestDto rejectRequest(Long requestId, String rejectionReason,
                                          Long userId, String userRole) {
        SupplyRequest request = requireRequest(requestId);
        requireSupplyingSideRole(request, userRole,
                "Seule l'entité sollicitée peut rejeter cette demande.");
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new BusinessException("Le motif du rejet est obligatoire.");
        }
        requireStatus(request, SupplyRequestStatus.SOUMISE, SupplyRequestStatus.EN_COURS_TRAITEMENT);

        request.setStatus(SupplyRequestStatus.REJETEE.name());
        request.setRejectionReason(rejectionReason.trim());
        request.setProcessedByUser(requireUser(userId));
        request.setUpdatedAt(LocalDateTime.now());
        return toDto(supplyRequestRepository.save(request));
    }

    @Override
    @Transactional
    public SupplyRequestDto takeCharge(Long requestId, Long userId, String userRole) {
        SupplyRequest request = requireRequest(requestId);
        requireSupplyingSideRole(request, userRole,
                "Seule l'entité sollicitée peut prendre en charge cette demande.");
        requireStatus(request, SupplyRequestStatus.SOUMISE);

        request.setStatus(SupplyRequestStatus.EN_COURS_TRAITEMENT.name());
        request.setProcessedByUser(requireUser(userId));
        request.setUpdatedAt(LocalDateTime.now());
        return toDto(supplyRequestRepository.save(request));
    }

    @Override
    @Transactional
    public SupplyRequestDto shipRequest(Long requestId, SupplyRequestProcessFormDto form,
                                        Long userId, String userRole) {
        SupplyRequest request = requireRequest(requestId);
        requireSupplyingSideRole(request, userRole,
                "Seule l'entité sollicitée peut expédier cette demande.");
        if (TYPE_DAAS.equals(request.getSupplyingEntity().getType())) {
            throw new BusinessException(
                    "Une demande adressée à la DAAS ne mouvemente pas de stock : elle est traitée "
                            + "par une commande fournisseur.");
        }
        requireStatus(request, SupplyRequestStatus.SOUMISE, SupplyRequestStatus.EN_COURS_TRAITEMENT);
        if (form == null || form.getApprovals() == null || form.getApprovals().isEmpty()) {
            throw new BusinessException("Les quantités accordées doivent être renseignées pour l'expédition.");
        }

        StructureEntity supplyingEntity = request.getSupplyingEntity();
        for (SupplyRequestApprovalLineDto approval : form.getApprovals()) {
            SupplyRequestItem item = requireItem(request, approval.getItemId());
            int approved = approval.getApprovedQuantity();
            if (approved > item.getQuantityRequested()) {
                throw new BusinessException("Quantité accordée supérieure à la quantité demandée pour "
                        + item.getProduct().getCode() + ".");
            }
            checkStockAvailability(supplyingEntity, item.getProduct(), approved);
        }

        BigDecimal totalValuation = BigDecimal.ZERO;
        for (SupplyRequestApprovalLineDto approval : form.getApprovals()) {
            SupplyRequestItem item = requireItem(request, approval.getItemId());
            int approved = approval.getApprovedQuantity();
            if (approved > 0) {
                recordStockMovement(supplyingEntity, item.getProduct(), MOUVEMENT_SORTIE, approved,
                        userId, request.getId());
                totalValuation = totalValuation.add(computeValuation(item, approved));
                item.setQuantityApproved(approved);
            } else {
                item.setQuantityApproved(0);
            }
        }

        request.setStatus(SupplyRequestStatus.EXPEDIEE.name());
        request.setProcessedByUser(requireUser(userId));
        request.setUpdatedAt(LocalDateTime.now());

        SupplyRequest saved = supplyRequestRepository.save(request);
        if (totalValuation.signum() > 0) {
            financeService.recordStockExitValuation(saved.getId(), totalValuation, userId,
                    "Valorisation des sorties de stock de la demande " + saved.getRequestNumber()
                            + " (" + supplyingEntity.getCode() + " → " + saved.getRequestingEntity().getCode() + ")");
        }
        return toDto(saved);
    }

    @Override
    @Transactional
    public SupplyRequestDto confirmDelivery(Long requestId, Long userId, String userRole, Long userEntityId) {
        SupplyRequest request = requireRequest(requestId);
        requireStatus(request, SupplyRequestStatus.EXPEDIEE);
        if (!ROLE_ADMIN.equals(userRole) && !request.getRequestingEntity().getId().equals(userEntityId)) {
            throw new BusinessException("Seule la structure demandeuse peut confirmer la livraison.");
        }
        if (TYPE_DAAS.equals(request.getSupplyingEntity().getType())) {
            throw new BusinessException(
                    "Une demande DC → DAAS est clôturée à la réception de la commande fournisseur au Dépôt Central.");
        }

        StructureEntity requestingEntity = request.getRequestingEntity();
        for (SupplyRequestItem item : request.getRequestItems()) {
            int quantity = item.getQuantityApproved() != null ? item.getQuantityApproved() : 0;
            if (quantity > 0) {
                recordStockMovement(requestingEntity, item.getProduct(), MOUVEMENT_ENTREE, quantity,
                        userId, request.getId());
                item.setQuantityDelivered(quantity);
            }
        }

        request.setStatus(SupplyRequestStatus.LIVREE.name());
        request.setUpdatedAt(LocalDateTime.now());
        return toDto(supplyRequestRepository.save(request));
    }

    // ------------------------------------------------------------------
    // Circuit DC → DAAS (piloté par les commandes fournisseurs)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public void markProcessingByDaas(Long requestId, Long daasUserId) {
        SupplyRequest request = requireRequest(requestId);
        requireStatus(request, SupplyRequestStatus.SOUMISE, SupplyRequestStatus.EN_COURS_TRAITEMENT);
        if (!TYPE_DAAS.equals(request.getSupplyingEntity().getType())) {
            throw new BusinessException("Cette demande n'est pas adressée à la DAAS.");
        }
        request.setStatus(SupplyRequestStatus.EN_COURS_TRAITEMENT.name());
        if (request.getProcessedByUser() == null) {
            request.setProcessedByUser(requireUser(daasUserId));
        }
        request.setUpdatedAt(LocalDateTime.now());
        supplyRequestRepository.save(request);
    }

    @Override
    @Transactional
    public SupplyRequestDto registerOrderDelivery(Order order) {
        SupplyRequest request = order.getSupplyRequest();
        if (request == null) {
            throw new BusinessException("La commande n'est liée à aucune demande d'approvisionnement.");
        }
        request = requireRequest(request.getId());

        for (var item : order.getOrderItems()) {
            int received = item.getQuantityReceived() != null ? item.getQuantityReceived() : 0;
            if (received <= 0) {
                continue;
            }
            request.getRequestItems().stream()
                    .filter(requestItem -> requestItem.getProduct().getId().equals(item.getProduct().getId()))
                    .findFirst()
                    .ifPresent(requestItem -> requestItem.setQuantityDelivered(
                            (requestItem.getQuantityDelivered() != null ? requestItem.getQuantityDelivered() : 0)
                                    + received));
        }

        boolean fullyCovered = request.getRequestItems().stream()
                .allMatch(requestItem -> (requestItem.getQuantityDelivered() != null
                        ? requestItem.getQuantityDelivered() : 0) >= requestItem.getQuantityRequested());
        if (fullyCovered) {
            request.setStatus(SupplyRequestStatus.LIVREE.name());
        }
        request.setUpdatedAt(LocalDateTime.now());
        return toDto(supplyRequestRepository.save(request));
    }

    // ------------------------------------------------------------------
    // Consultation
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<SupplyRequestDto> findOpenRequestsForDaas() {
        return supplyRequestRepository.findOpenRequestsForDaas().stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplyRequestDto> findRequests(Long userEntityId, String userRole,
                                               Long requestingEntityId, String status) {        Long filter = RoleNames.isRestrictedToOwnEntity(userRole) ? userEntityId : requestingEntityId;
        String statusValue = SupplyRequestStatus.fromValue(status) == null
                ? null : SupplyRequestStatus.fromValue(status).name();
        return supplyRequestRepository.search(filter, null, statusValue)
                .stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupplyRequestDto> findRequestDetail(Long requestId, Long userEntityId, String userRole) {
        return supplyRequestRepository.findByIdWithDetails(requestId)
                .filter(request -> !RoleNames.isRestrictedToOwnEntity(userRole)
                        || request.getRequestingEntity().getId().equals(userEntityId)
                        || request.getSupplyingEntity().getId().equals(userEntityId))
                .map(this::toDto);
    }

    // ------------------------------------------------------------------
    // Règles internes
    // ------------------------------------------------------------------

    /**
     * Relations autorisées : CM → PC, PC → DC, DC → DAAS. Le rôle de
     * l'utilisateur doit correspondre à la structure demandeuse.
     */
    private void validatePairAndRole(StructureEntity requester, StructureEntity supplier, String userRole) {
        String requestingType = requester.getType();
        String supplyingType = supplier.getType();
        boolean allowedPair;
        boolean allowedRole;
        switch (requestingType == null ? "" : requestingType) {
            case TYPE_CM -> {
                allowedPair = TYPE_PC.equals(supplyingType);
                allowedRole = List.of("CHEF_SERVICE", "AGENT_APPROVISIONNEMENT", ROLE_ADMIN).contains(userRole);
            }
            case TYPE_PC -> {
                allowedPair = TYPE_DC.equals(supplyingType);
                allowedRole = List.of(ROLE_RESPONSABLE_PHARMACIE, ROLE_ADMIN).contains(userRole);
            }
            case TYPE_DC -> {
                allowedPair = TYPE_DAAS.equals(supplyingType);
                allowedRole = List.of(ROLE_RESPONSABLE_DEPOT, ROLE_MAGASINIER, ROLE_ADMIN).contains(userRole);
            }
            default -> {
                allowedPair = false;
                allowedRole = false;
            }
        }
        if (requester.getId().equals(supplier.getId())) {
            throw new BusinessException(
                    "La structure demandeuse et la structure sollicitée doivent être différentes.");
        }
        if (!allowedPair) {
            throw new BusinessException("Relation d'approvisionnement non autorisée : "
                    + requestingType + " → " + supplyingType
                    + ". Les seules relations permises sont CM → PC, PC → DC et DC → DAAS.");
        }
        if (!allowedRole) {
            throw new BusinessException("Vous n'êtes pas autorisé à créer une demande au nom de "
                    + requester.getName() + ".");
        }
    }

    private void requireSupplyingSideRole(SupplyRequest request, String userRole, String message) {
        String supplyingType = request.getSupplyingEntity().getType();
        boolean allowed = switch (supplyingType) {
            case TYPE_PC -> List.of(ROLE_RESPONSABLE_PHARMACIE, ROLE_ADMIN).contains(userRole);
            case TYPE_DC -> List.of(ROLE_RESPONSABLE_DEPOT, ROLE_MAGASINIER, ROLE_ADMIN).contains(userRole);
            case TYPE_DAAS -> List.of(ROLE_DAAS, ROLE_ADMIN).contains(userRole);
            default -> false;
        };
        if (!allowed) {
            throw new BusinessException(message);
        }
    }

    private List<SupplyRequestLineFormDto> sanitizeLines(List<SupplyRequestLineFormDto> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException("La demande doit contenir au moins une ligne de produit.");
        }
        Set<Long> seen = new HashSet<>();
        List<SupplyRequestLineFormDto> cleaned = new ArrayList<>();
        for (SupplyRequestLineFormDto line : lines) {
            if (line == null || line.getProductId() == null || line.getQuantityRequested() == null
                    || line.getQuantityRequested() < 1) {
                throw new BusinessException(
                        "Chaque ligne doit désigner un produit avec une quantité d'au moins 1.");
            }
            if (!seen.add(line.getProductId())) {
                throw new BusinessException(
                        "Le produit id=" + line.getProductId() + " figure deux fois dans la demande.");
            }
            cleaned.add(line);
        }
        return cleaned;
    }

    private String generateRequestNumber() {
        String year = String.valueOf(LocalDateTime.now().getYear());
        String last = supplyRequestRepository.findLastRequestNumberOfYear(year);
        int next = 1;
        if (last != null && last.matches("DEM-\\d{4}-\\d{4,}")) {
            next = Integer.parseInt(last.substring(last.lastIndexOf('-') + 1)) + 1;
        }
        return String.format("DEM-%s-%04d", year, next);
    }

    private SupplyRequestItem requireItem(SupplyRequest request, Long itemId) {
        return request.getRequestItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Ligne de demande introuvable : id=" + itemId));
    }

    private void checkStockAvailability(StructureEntity entity, Product product, int quantity) {
        int available = stockRepository.findByEntityIdAndProductId(entity.getId(), product.getId())
                .map(stock -> stock.getQuantity() != null ? stock.getQuantity() : 0)
                .orElse(0);
        if (quantity > available) {
            throw new BusinessException("Stock insuffisant de " + entity.getCode() + " pour "
                    + product.getDesignation() + " : " + available + " " + product.getUnit()
                    + " disponible(s), " + quantity + " requis.");
        }
    }

    private void recordStockMovement(StructureEntity entity, Product product, String type,
                                     int quantity, Long userId, Long supplyRequestId) {
        stockService.recordMovement(new StockMovementFormDto(entity.getId(), product.getId(), type, quantity),
                userId, supplyRequestId, null);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Utilisateur introuvable : id=" + userId));
    }

    private SupplyRequest requireRequest(Long requestId) {
        return supplyRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new BusinessException(
                        "Demande d'approvisionnement introuvable : id=" + requestId));
    }

    private void requireStatus(SupplyRequest request, SupplyRequestStatus... expected) {
        SupplyRequestStatus current = SupplyRequestStatus.fromValue(request.getStatus());
        for (SupplyRequestStatus candidate : expected) {
            if (candidate == current) {
                return;
            }
        }
        throw new BusinessException("Action non permise : la demande est à l'état "
                + request.getStatus() + ".");
    }

    private void requireDraftOwner(SupplyRequest request, Long userId, String userRole) {
        if (!ROLE_ADMIN.equals(userRole) && !request.getCreatedByUser().getId().equals(userId)) {
            throw new BusinessException("Seul l'auteur de la demande peut la soumettre.");
        }
    }

    private BigDecimal computeValuation(SupplyRequestItem item, int approved) {
        BigDecimal unitPrice = item.getProduct().getUnitPrice() != null
                ? item.getProduct().getUnitPrice() : BigDecimal.ZERO;
        return unitPrice.multiply(BigDecimal.valueOf(approved));
    }

    private SupplyRequestDto toDto(SupplyRequest request) {
        List<SupplyRequestItemDto> items = request.getRequestItems() == null ? List.of()
                : request.getRequestItems().stream()
                        .map(item -> new SupplyRequestItemDto(
                                item.getId(),
                                item.getProduct().getId(),
                                item.getProduct().getCode(),
                                item.getProduct().getDesignation(),
                                item.getProduct().getUnit(),
                                item.getQuantityRequested(),
                                item.getQuantityApproved(),
                                item.getQuantityDelivered()))
                        .toList();
        return new SupplyRequestDto(
                request.getId(),
                request.getRequestNumber(),
                request.getRequestingEntity().getId(),
                request.getRequestingEntity().getCode(),
                request.getRequestingEntity().getName(),
                request.getSupplyingEntity().getId(),
                request.getSupplyingEntity().getCode(),
                request.getSupplyingEntity().getName(),
                request.getSupplyingEntity().getType(),
                request.getStatus(),
                request.getRejectionReason(),
                formatUserName(request.getCreatedByUser()),
                request.getProcessedByUser() != null ? formatUserName(request.getProcessedByUser()) : null,
                request.getCreatedAt(),
                request.getUpdatedAt(),
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
