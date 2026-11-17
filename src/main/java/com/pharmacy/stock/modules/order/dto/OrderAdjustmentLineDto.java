package com.pharmacy.stock.modules.order.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ligne de réajustement des quantités par la DBI.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderAdjustmentLineDto extends BaseDto {

    @NotNull(message = "L'identifiant de la ligne est obligatoire")
    private Long itemId;

    @NotNull(message = "La quantité ajustée de chaque ligne est obligatoire")
    @Min(value = 0, message = "La quantité ajustée doit être positive ou nulle")
    private Integer adjustedQuantity;
}
