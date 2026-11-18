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
 * Formulaire de création d'une commande fournisseur par la DAAS.
 * La demande d'approvisionnement du Dépôt Central à l'origine de la
 * commande est optionnelle (une commande peut être émise de façon autonome).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderFormDto extends BaseDto {

    @NotNull(message = "Le fournisseur est obligatoire")
    private Long supplierEntityId;

    /** Demande d'approvisionnement DC → DAAS liée (optionnelle). */
    private Long supplyRequestId;

    private List<OrderLineFormDto> lines = new ArrayList<>();
}
