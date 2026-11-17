package com.pharmacy.stock.modules.product.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Formulaire de création / modification d'un produit du catalogue.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductFormDto extends BaseDto {

    @NotBlank(message = "Le code du produit est obligatoire")
    @Size(max = 50, message = "50 caractères maximum")
    private String code;

    @NotBlank(message = "La désignation est obligatoire")
    @Size(max = 150, message = "150 caractères maximum")
    private String designation;

    @NotBlank(message = "L'unité de dispensation est obligatoire")
    @Size(max = 20, message = "20 caractères maximum")
    private String unit;

    @DecimalMin(value = "0.00", message = "Le prix unitaire doit être positif ou nul")
    private BigDecimal unitPrice;
}
