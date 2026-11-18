package com.pharmacy.stock.modules.stock.service;

import com.pharmacy.stock.common.exception.BusinessException;
import com.pharmacy.stock.modules.entity.entity.StructureEntity;
import com.pharmacy.stock.modules.entity.repository.StructureEntityRepository;
import com.pharmacy.stock.modules.product.entity.Product;
import com.pharmacy.stock.modules.product.repository.ProductRepository;
import com.pharmacy.stock.modules.order.repository.OrderRepository;
import com.pharmacy.stock.modules.supply.repository.SupplyRequestRepository;
import com.pharmacy.stock.modules.stock.dto.StockDto;
import com.pharmacy.stock.modules.stock.dto.StockMovementDto;
import com.pharmacy.stock.modules.stock.dto.StockMovementFormDto;
import com.pharmacy.stock.modules.stock.dto.StockThresholdFormDto;
import com.pharmacy.stock.modules.stock.entity.Stock;
import com.pharmacy.stock.modules.stock.entity.StockMovement;
import com.pharmacy.stock.modules.stock.entity.StockMovementType;
import com.pharmacy.stock.modules.stock.repository.StockMovementRepository;
import com.pharmacy.stock.modules.stock.repository.StockRepository;
import com.pharmacy.stock.modules.user.repository.UserRepository;
import com.pharmacy.stock.modules.user.security.RoleNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    private static final int DEFAULT_MIN_THRESHOLD = 10;

    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StructureEntityRepository structureEntityRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final SupplyRequestRepository supplyRequestRepository;

    @Override
    @Transactional(readOnly = true)
    public List<StockDto> findStocks(Long entityId, boolean lowStockOnly) {
        if (lowStockOnly) {
            return filterByEntity(stockRepository.findLowStocksWithDetails(), entityId).stream()
                    .map(this::toStockDto).toList();
        }
        List<Stock> stocks = entityId == null
                ? stockRepository.findAllWithDetails()
                : stockRepository.findByEntityWithDetails(entityId);
        return stocks.stream().map(this::toStockDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockDto> findLowStocks() {
        return stockRepository.findLowStocksWithDetails().stream().map(this::toStockDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockDto> findStockById(Long stockId) {
        return stockRepository.findById(stockId).map(this::toStockDto);
    }

    @Override
    @Transactional
    public StockDto updateThreshold(Long stockId, StockThresholdFormDto form, String userRole) {
        if (RoleNames.isRestrictedToOwnEntity(userRole)) {
            throw new BusinessException(
                    "Seuls l'administrateur et les responsables de structure peuvent modifier le seuil d'alerte.");
        }
        return updateThreshold(stockId, form);
    }

    /**
     * Méthode interne (circuit des commandes) sans contrôle de rôle : les
     * points d'entrée utilisateur passent par {@link #recordUserMovement}
     * et la surcharge avec rôle pour updateThreshold.
     */
    @Transactional
    public StockDto updateThreshold(Long stockId, StockThresholdFormDto form) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new BusinessException("Ligne de stock introuvable : id=" + stockId));

        stock.setMinThreshold(form.getMinThreshold());
        stock.setUpdatedAt(LocalDateTime.now());
        return toStockDto(stockRepository.save(stock));
    }

    @Override
    @Transactional
    public StockMovementDto recordUserMovement(StockMovementFormDto form, Long userId,
                                               String userRole, Long userEntityId) {
        if (RoleNames.isRestrictedToOwnEntity(userRole) && !userEntityId.equals(form.getEntityId())) {
            throw new BusinessException(
                    "Vous ne pouvez enregistrer des mouvements que sur votre structure de rattachement.");
        }
        return recordMovement(form, userId);
    }

    @Override
    @Transactional
    public StockMovementDto recordMovement(StockMovementFormDto form, Long userId) {
        return recordMovement(form, userId, null, null);
    }

    @Override
    @Transactional
    public StockMovementDto recordMovement(StockMovementFormDto form, Long userId,
                                           Long referenceSupplyRequestId, Long referenceOrderId) {
        if (referenceSupplyRequestId != null && referenceOrderId != null) {
            throw new BusinessException(
                    "Un mouvement ne peut référencer à la fois une demande d'approvisionnement et une commande.");
        }
        StockMovementType type = StockMovementType.fromValue(form.getType());
        if (type == null) {
            throw new BusinessException("Type de mouvement invalide : " + form.getType());
        }

        StructureEntity entity = structureEntityRepository.findById(form.getEntityId())
                .orElseThrow(() -> new BusinessException("Structure introuvable : id=" + form.getEntityId()));
        Product product = productRepository.findById(form.getProductId())
                .orElseThrow(() -> new BusinessException("Produit introuvable : id=" + form.getProductId()));

        int quantity = form.getQuantity();
        Stock stock = getOrCreateStock(entity, product);
        int stockBefore = stock.getQuantity() != null ? stock.getQuantity() : 0;
        int stockAfter;
        int movementQuantity;

        switch (type) {
            case ENTREE -> {
                stockAfter = stockBefore + quantity;
                movementQuantity = quantity;
            }
            case SORTIE -> {
                if (quantity > stockBefore) {
                    throw new BusinessException("Stock insuffisant pour " + product.getDesignation()
                            + " : " + stockBefore + " " + product.getUnit() + " disponible(s), "
                            + quantity + " demandé(s).");
                }
                stockAfter = stockBefore - quantity;
                movementQuantity = quantity;
            }
            case AJUSTEMENT_INVENTAIRE -> {
                // La quantité saisie correspond au comptage physique réel.
                stockAfter = quantity;
                movementQuantity = stockAfter - stockBefore;
            }
            default -> throw new BusinessException("Type de mouvement non géré : " + type);
        }

        stock.setQuantity(stockAfter);
        stock.setUpdatedAt(LocalDateTime.now());
        stockRepository.save(stock);

        StockMovement movement = new StockMovement();
        movement.setEntity(entity);
        movement.setProduct(product);
        movement.setType(type.name());
        movement.setQuantity(movementQuantity);
        movement.setStockBefore(stockBefore);
        movement.setStockAfter(stockAfter);
        if (referenceSupplyRequestId != null) {
            movement.setReferenceSupplyRequest(supplyRequestRepository.getReferenceById(referenceSupplyRequestId));
        }
        if (referenceOrderId != null) {
            movement.setReferenceOrder(orderRepository.getReferenceById(referenceOrderId));
        }
        movement.setCreatedByUser(userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Utilisateur introuvable : id=" + userId)));
        movement.setCreatedAt(LocalDateTime.now());

        return toMovementDto(stockMovementRepository.save(movement));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockMovementDto> findMovements(Long entityId, Long productId, String type) {
        return stockMovementRepository.search(
                        entityId,
                        productId,
                        StockMovementType.fromValue(type) != null ? StockMovementType.fromValue(type).name() : null)
                .stream().map(this::toMovementDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countStocks() {
        return stockRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long countLowStocks() {
        return stockRepository.findLowStocksWithDetails().size();
    }

    private List<Stock> filterByEntity(List<Stock> stocks, Long entityId) {
        if (entityId == null) {
            return stocks;
        }
        return stocks.stream().filter(stock -> entityId.equals(stock.getEntity().getId())).toList();
    }

    private Stock getOrCreateStock(StructureEntity entity, Product product) {
        return stockRepository.findByEntityIdAndProductId(entity.getId(), product.getId())
                .orElseGet(() -> {
                    Stock created = new Stock();
                    created.setEntity(entity);
                    created.setProduct(product);
                    created.setQuantity(0);
                    created.setMinThreshold(DEFAULT_MIN_THRESHOLD);
                    created.setUpdatedAt(LocalDateTime.now());
                    return created;
                });
    }

    private StockDto toStockDto(Stock stock) {
        return new StockDto(
                stock.getId(),
                stock.getEntity().getId(),
                stock.getEntity().getCode(),
                stock.getEntity().getName(),
                stock.getProduct().getId(),
                stock.getProduct().getCode(),
                stock.getProduct().getDesignation(),
                stock.getProduct().getUnit(),
                stock.getQuantity(),
                stock.getMinThreshold(),
                stock.getUpdatedAt());
    }

    private StockMovementDto toMovementDto(StockMovement movement) {
        return new StockMovementDto(
                movement.getId(),
                movement.getEntity().getId(),
                movement.getEntity().getCode(),
                movement.getEntity().getName(),
                movement.getProduct().getId(),
                movement.getProduct().getCode(),
                movement.getProduct().getDesignation(),
                movement.getType(),
                movement.getQuantity(),
                movement.getStockBefore(),
                movement.getStockAfter(),
                movement.getReferenceOrder() != null ? movement.getReferenceOrder().getOrderNumber() : null,
                movement.getReferenceSupplyRequest() != null
                        ? movement.getReferenceSupplyRequest().getRequestNumber() : null,
                formatUserName(movement),
                movement.getCreatedAt());
    }

    private String formatUserName(StockMovement movement) {
        String first = movement.getCreatedByUser().getFirstName();
        String last = movement.getCreatedByUser().getLastName();
        if (first == null && last == null) {
            return movement.getCreatedByUser().getUsername();
        }
        return ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
    }
}
