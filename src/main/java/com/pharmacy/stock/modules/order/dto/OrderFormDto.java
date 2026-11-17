package com.pharmacy.stock.modules.order.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Formulaire de création d'une demande / commande de produits pharmaceutiques.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderFormDto extends BaseDto {

    @NotNull(message = "La structure destinataire est obligatoire")
    private Long targetEntityId;

    private List<OrderLineFormDto> lines = new ArrayList<>();
}
