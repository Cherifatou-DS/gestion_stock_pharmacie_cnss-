package com.pharmacy.stock.modules.supply.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ligne de demande d'approvisionnement en lecture.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupplyRequestItemDto extends BaseDto {

    private Long id;

    private Long productId;

    private String productCode;

    private String productDesignation;

    private String productUnit;

    private Integer quantityRequested;

    private Integer quantityApproved;

    private Integer quantityDelivered;
}
