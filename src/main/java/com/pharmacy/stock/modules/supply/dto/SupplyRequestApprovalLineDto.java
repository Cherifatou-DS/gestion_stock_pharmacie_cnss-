package com.pharmacy.stock.modules.supply.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Quantité accordée par l'entité sollicitée pour une ligne de demande
 * d'approvisionnement (peut être inférieure à la quantité demandée).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupplyRequestApprovalLineDto extends BaseDto {

    @NotNull(message = "L'identifiant de la ligne est obligatoire")
    private Long itemId;

    @NotNull(message = "La quantité accordée de chaque ligne est obligatoire")
    @Min(value = 0, message = "La quantité accordée doit être positive ou nulle")
    private Integer approvedQuantity;
}
