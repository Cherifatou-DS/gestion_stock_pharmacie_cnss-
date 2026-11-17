package com.pharmacy.stock.modules.stock.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Formulaire de modification du seuil d'alerte de réapprovisionnement d'une ligne de stock.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockThresholdFormDto extends BaseDto {

    @NotNull(message = "Le seuil d'alerte est obligatoire")
    @Min(value = 0, message = "Le seuil d'alerte doit être positif ou nul")
    private Integer minThreshold;
}
