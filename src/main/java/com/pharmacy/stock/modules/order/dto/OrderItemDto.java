package com.pharmacy.stock.modules.order.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ligne de commande en lecture (produit + quantités du circuit).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDto extends BaseDto {

    private Long id;

    private Long productId;

    private String productCode;

    private String productDesignation;

    private String productUnit;

    private Integer quantityRequested;

    private Integer quantityAdjusted;

    private Integer quantityDelivered;
}
