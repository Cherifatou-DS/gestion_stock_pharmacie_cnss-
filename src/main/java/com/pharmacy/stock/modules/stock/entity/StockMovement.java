package com.pharmacy.stock.modules.stock.entity;

import com.pharmacy.stock.modules.entity.entity.StructureEntity;
import com.pharmacy.stock.modules.order.entity.Order;
import com.pharmacy.stock.modules.product.entity.Product;
import com.pharmacy.stock.modules.supply.entity.SupplyRequest;
import com.pharmacy.stock.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id", nullable = false)
    private StructureEntity entity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "stock_before", nullable = false)
    private Integer stockBefore;

    @Column(name = "stock_after", nullable = false)
    private Integer stockAfter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference_order_id")
    private Order referenceOrder;

    /** Mouvement lié à une demande d'approvisionnement (référence exclusive avec referenceOrder). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference_supply_request_id")
    private SupplyRequest referenceSupplyRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
