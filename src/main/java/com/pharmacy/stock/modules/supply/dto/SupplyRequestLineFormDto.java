package com.pharmacy.stock.modules.supply.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ligne de produit saisie dans le formulaire de demande d'approvisionnement.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupplyRequestLineFormDto extends BaseDto {

    @NotNull(message = "Le produit de chaque ligne est obligatoire")
    private Long productId;

    @NotNull(message = "La quantité demandée de chaque ligne est obligatoire")
    @Min(value = 1, message = "La quantité demandée doit être au moins égale à 1")
    private Integer quantityRequested;
}
