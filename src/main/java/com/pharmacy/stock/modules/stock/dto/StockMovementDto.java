package com.pharmacy.stock.modules.stock.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Représentation en lecture d'un mouvement de stock (journal d'inventaire).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementDto extends BaseDto {

    private Long id;

    private Long entityId;

    private String entityCode;

    private String entityName;

    private Long productId;

    private String productCode;

    private String productDesignation;

    private String type;

    private Integer quantity;

    private Integer stockBefore;

    private Integer stockAfter;

    private String createdByName;

    private LocalDateTime createdAt;
}
