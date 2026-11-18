package com.pharmacy.stock.modules.order.entity;

import com.pharmacy.stock.modules.entity.entity.StructureEntity;
import com.pharmacy.stock.modules.supply.entity.SupplyRequest;
import com.pharmacy.stock.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Commande fournisseur : exclusivement passée par la DAAS auprès d'un
 * fournisseur pour approvisionner le Dépôt Central, soumise à la validation
 * systématique de la DBI, réceptionnée par le Dépôt Central.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    /** Émetteur de la commande : la DAAS. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_entity_id", nullable = false)
    private StructureEntity sourceEntity;

    /** Fournisseur destinataire de la commande. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_entity_id", nullable = false)
    private StructureEntity supplierEntity;

    /** Structure de réception : le Dépôt Central. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiving_entity_id", nullable = false)
    private StructureEntity receivingEntity;

    /** Demande d'approvisionnement du Dépôt Central à l'origine de la commande (DC → DAAS). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supply_request_id")
    private SupplyRequest supplyRequest;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "total_amount", precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by_user_id")
    private User validatedByUser;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by_user_id")
    private User receivedByUser;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();
}
