package com.pharmacy.stock.modules.finance.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction financière en lecture (suivi DBI).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FinancialTransactionDto extends BaseDto {

    private Long id;

    private String transactionType;

    private String transactionTypeLabel;

    private BigDecimal amount;

    /** Numéro de la commande fournisseur liée (engagement), null sinon. */
    private String referenceOrderNumber;

    /** Numéro de la demande d'approvisionnement liée (sortie valorisée), null sinon. */
    private String referenceSupplyRequestNumber;

    private String recordedByName;

    private String comment;

    private LocalDateTime createdAt;
}
