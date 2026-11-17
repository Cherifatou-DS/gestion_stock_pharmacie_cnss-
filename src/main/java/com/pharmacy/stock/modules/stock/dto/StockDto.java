package com.pharmacy.stock.modules.stock.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Représentation en lecture d'une ligne de stock d'une entité (PC, DC, CM).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockDto extends BaseDto {

    private Long id;

    private Long entityId;

    private String entityCode;

    private String entityName;

    private Long productId;

    private String productCode;

    private String productDesignation;

    private String productUnit;

    private Integer quantity;

    private Integer minThreshold;

    private LocalDateTime updatedAt;

    /**
     * Indique si le stock est au niveau ou en dessous du seuil d'alerte.
     */
    public boolean isLowStock() {
        return quantity != null && minThreshold != null && quantity <= minThreshold;
    }
}
