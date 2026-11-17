package com.pharmacy.stock.modules.stock.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Formulaire d'enregistrement d'un mouvement de stock manuel.
 *
 * Pour ENTREE / SORTIE : quantity = quantité mouvementée.
 * Pour AJUSTEMENT_INVENTAIRE : quantity = quantité comptée physiquement.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementFormDto extends BaseDto {

    @NotNull(message = "La structure est obligatoire")
    private Long entityId;

    @NotNull(message = "Le produit est obligatoire")
    private Long productId;

    @NotBlank(message = "Le type de mouvement est obligatoire")
    private String type;

    @NotNull(message = "La quantité est obligatoire")
    @Min(value = 0, message = "La quantité doit être positive ou nulle")
    private Integer quantity;
}
