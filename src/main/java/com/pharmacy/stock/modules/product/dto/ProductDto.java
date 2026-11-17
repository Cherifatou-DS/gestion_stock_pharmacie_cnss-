package com.pharmacy.stock.modules.product.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Représentation en lecture d'un produit pharmaceutique du catalogue.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto extends BaseDto {

    private Long id;

    private String code;

    private String designation;

    private String unit;

    private BigDecimal unitPrice;

    private LocalDateTime createdAt;
}
