package com.pharmacy.stock.modules.finance.entity;

import com.pharmacy.stock.modules.order.entity.Order;
import com.pharmacy.stock.modules.supply.entity.SupplyRequest;
import com.pharmacy.stock.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction financière enregistrée par la DBI :
 * engagement fournisseur (validation d'une commande) ou sortie de stock
 * valorisée (expédition d'une demande d'approvisionnement).
 */
@Entity
@Table(name = "financial_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FinancialTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_type", nullable = false, length = 40)
    private String transactionType;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference_supply_request_id")
    private SupplyRequest referenceSupplyRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference_order_id")
    private Order referenceOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_user_id", nullable = false)
    private User recordedByUser;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
