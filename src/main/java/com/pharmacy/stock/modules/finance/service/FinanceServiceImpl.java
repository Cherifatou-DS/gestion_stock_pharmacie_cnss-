package com.pharmacy.stock.modules.finance.service;

import com.pharmacy.stock.common.exception.BusinessException;
import com.pharmacy.stock.modules.finance.dto.FinanceSummaryDto;
import com.pharmacy.stock.modules.finance.dto.FinancialTransactionDto;
import com.pharmacy.stock.modules.finance.entity.FinancialTransaction;
import com.pharmacy.stock.modules.finance.entity.FinancialTransactionType;
import com.pharmacy.stock.modules.finance.repository.FinancialTransactionRepository;
import com.pharmacy.stock.modules.order.repository.OrderRepository;
import com.pharmacy.stock.modules.supply.repository.SupplyRequestRepository;
import com.pharmacy.stock.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {

    private final FinancialTransactionRepository financialTransactionRepository;
    private final OrderRepository orderRepository;
    private final SupplyRequestRepository supplyRequestRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public FinancialTransactionDto recordSupplierEngagement(Long orderId, BigDecimal amount,
                                                            Long recordedByUserId, String comment) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setTransactionType(FinancialTransactionType.ENGAGEMENT_FOURNISSEUR.name());
        transaction.setAmount(requirePositive(amount));
        transaction.setReferenceOrder(orderRepository.getReferenceById(orderId));
        transaction.setRecordedByUser(userRepository.findById(recordedByUserId)
                .orElseThrow(() -> new BusinessException("Utilisateur introuvable : id=" + recordedByUserId)));
        transaction.setComment(comment);
        transaction.setCreatedAt(java.time.LocalDateTime.now());
        return toDto(financialTransactionRepository.save(transaction));
    }

    @Override
    @Transactional
    public FinancialTransactionDto recordStockExitValuation(Long supplyRequestId, BigDecimal amount,
                                                            Long recordedByUserId, String comment) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setTransactionType(FinancialTransactionType.SORTIE_STOCK_VALORISEE.name());
        transaction.setAmount(requirePositive(amount));
        transaction.setReferenceSupplyRequest(supplyRequestRepository.getReferenceById(supplyRequestId));
        transaction.setRecordedByUser(userRepository.findById(recordedByUserId)
                .orElseThrow(() -> new BusinessException("Utilisateur introuvable : id=" + recordedByUserId)));
        transaction.setComment(comment);
        transaction.setCreatedAt(java.time.LocalDateTime.now());
        return toDto(financialTransactionRepository.save(transaction));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FinancialTransactionDto> findTransactions(String transactionType) {
        String type = FinancialTransactionType.fromValue(transactionType) == null
                ? null
                : FinancialTransactionType.fromValue(transactionType).name();
        return financialTransactionRepository.search(type).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FinanceSummaryDto summarize() {
        return new FinanceSummaryDto(
                financialTransactionRepository.totalByType(FinancialTransactionType.ENGAGEMENT_FOURNISSEUR.name()),
                financialTransactionRepository.totalByType(FinancialTransactionType.SORTIE_STOCK_VALORISEE.name()));
    }

    private BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new BusinessException("Le montant d'une transaction financière doit être positif.");
        }
        return amount;
    }

    private FinancialTransactionDto toDto(FinancialTransaction transaction) {
        return new FinancialTransactionDto(
                transaction.getId(),
                transaction.getTransactionType(),
                FinancialTransactionType.fromValue(transaction.getTransactionType()) == null
                        ? transaction.getTransactionType()
                        : FinancialTransactionType.fromValue(transaction.getTransactionType()).getLabel(),
                transaction.getAmount(),
                transaction.getReferenceOrder() != null ? transaction.getReferenceOrder().getOrderNumber() : null,
                transaction.getReferenceSupplyRequest() != null
                        ? transaction.getReferenceSupplyRequest().getRequestNumber() : null,
                formatUserName(transaction),
                transaction.getComment(),
                transaction.getCreatedAt());
    }

    private String formatUserName(FinancialTransaction transaction) {
        var user = transaction.getRecordedByUser();
        if (user.getFirstName() == null && user.getLastName() == null) {
            return user.getUsername();
        }
        return ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                + (user.getLastName() != null ? user.getLastName() : "")).trim();
    }
}
