package com.pharmacy.stock.modules.finance.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Synthèse financière DBI (tableau de bord finance).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FinanceSummaryDto extends BaseDto {

    /** Total des engagements fournisseurs (commandes validées). */
    private BigDecimal totalEngagements;

    /** Total des sorties de stock valorisées (expéditions de demandes). */
    private BigDecimal totalSortiesValorisees;
}
