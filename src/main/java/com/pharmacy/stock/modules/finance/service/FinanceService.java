package com.pharmacy.stock.modules.finance.service;

import com.pharmacy.stock.modules.finance.dto.FinanceSummaryDto;
import com.pharmacy.stock.modules.finance.dto.FinancialTransactionDto;

import java.util.List;

public interface FinanceService {

    /**
     * Enregistrement d'un engagement fournisseur à la validation d'une commande par la DBI.
     */
    FinancialTransactionDto recordSupplierEngagement(Long orderId, java.math.BigDecimal amount,
                                                     Long recordedByUserId, String comment);

    /**
     * Enregistrement d'une sortie de stock valorisée à l'expédition d'une demande
     * d'approvisionnement (CM → PC ou PC → DC).
     */
    FinancialTransactionDto recordStockExitValuation(Long supplyRequestId, java.math.BigDecimal amount,
                                                     Long recordedByUserId, String comment);

    /**
     * Liste des transactions financières, avec filtre optionnel par type.
     */
    List<FinancialTransactionDto> findTransactions(String transactionType);

    /**
     * Synthèse des totaux par type de transaction.
     */
    FinanceSummaryDto summarize();
}
