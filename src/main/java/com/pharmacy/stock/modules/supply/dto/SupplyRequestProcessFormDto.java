package com.pharmacy.stock.modules.supply.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Formulaire de traitement d'une demande d'approvisionnement par l'entité
 * sollicitée : quantités accordées (expédition) et/ou motif de rejet.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupplyRequestProcessFormDto extends BaseDto {

    private List<SupplyRequestApprovalLineDto> approvals = new ArrayList<>();

    private String rejectionReason;
}
