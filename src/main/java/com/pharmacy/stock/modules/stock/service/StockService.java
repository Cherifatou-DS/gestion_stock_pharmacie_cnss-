package com.pharmacy.stock.modules.stock.service;

import com.pharmacy.stock.modules.stock.dto.StockDto;
import com.pharmacy.stock.modules.stock.dto.StockMovementDto;
import com.pharmacy.stock.modules.stock.dto.StockMovementFormDto;
import com.pharmacy.stock.modules.stock.dto.StockThresholdFormDto;

import java.util.List;
import java.util.Optional;

public interface StockService {

    /**
     * Consultation des stocks, avec filtres optionnels par entité et alerte de seuil.
     */
    List<StockDto> findStocks(Long entityId, boolean lowStockOnly);

    List<StockDto> findLowStocks();

    Optional<StockDto> findStockById(Long stockId);

    /**
     * Mise à jour du seuil d'alerte de réapprovisionnement d'une ligne de stock.
     */
    StockDto updateThreshold(Long stockId, StockThresholdFormDto form);

    /**
     * Enregistrement d'un mouvement de stock (ENTREE, SORTIE ou AJUSTEMENT_INVENTAIRE)
     * avec mise à jour atomique de la ligne de stock associée.
     */
    StockMovementDto recordMovement(StockMovementFormDto form, Long userId);

    List<StockMovementDto> findMovements(Long entityId, Long productId, String type);

    long countStocks();

    long countLowStocks();
}
