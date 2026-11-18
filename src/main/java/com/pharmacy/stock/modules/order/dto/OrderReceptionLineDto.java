package com.pharmacy.stock.modules.order.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Quantité réceptionnée pour une ligne de commande (Dépôt Central).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderReceptionLineDto extends BaseDto {

    @NotNull(message = "L'identifiant de la ligne est obligatoire")
    private Long itemId;

    @NotNull(message = "La quantité reçue de chaque ligne est obligatoire")
    @Min(value = 0, message = "La quantité reçue doit être positive ou nulle")
    private Integer receivedQuantity;
}
