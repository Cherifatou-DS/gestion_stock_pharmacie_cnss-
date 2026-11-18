package com.pharmacy.stock.modules.supply.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Formulaire de création d'une demande d'approvisionnement.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupplyRequestFormDto extends BaseDto {

    @NotNull(message = "La structure sollicitée est obligatoire")
    private Long supplyingEntityId;

    private List<SupplyRequestLineFormDto> lines = new ArrayList<>();
}
