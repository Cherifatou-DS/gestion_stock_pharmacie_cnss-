package com.pharmacy.stock.modules.order.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Ligne de commande fournisseur en lecture (produit + quantités du circuit).
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

    private Integer quantityReceived;

    private BigDecimal unitPrice;

    /**
     * Quantité de référence du circuit (ajustement DBI si présent).
     */
    public int getEffectiveQuantity() {
        return quantityAdjusted != null ? quantityAdjusted
                : (quantityRequested != null ? quantityRequested : 0);
    }

    /**
     * Montant de la ligne (quantité de référence × prix unitaire).
     */
    public BigDecimal getLineTotal() {
        return (unitPrice != null ? unitPrice : BigDecimal.ZERO)
                .multiply(BigDecimal.valueOf(getEffectiveQuantity()));
    }
}
