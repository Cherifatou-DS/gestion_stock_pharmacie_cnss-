package com.pharmacy.stock.modules.order.entity;

import com.pharmacy.stock.modules.product.entity.Product;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity_requested", nullable = false)
    private Integer quantityRequested;

    /** Ajustement de la DBI. */
    @Column(name = "quantity_adjusted")
    private Integer quantityAdjusted;

    /** Quantité réceptionnée par le Dépôt Central. */
    @Column(name = "quantity_received")
    private Integer quantityReceived = 0;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    /**
     * Quantité de référence : l'ajustement DBI s'il existe, sinon la quantité demandée.
     */
    public int effectiveQuantity() {
        return quantityAdjusted != null ? quantityAdjusted : quantityRequested;
    }
}
