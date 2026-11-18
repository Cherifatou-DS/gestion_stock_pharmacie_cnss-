package com.pharmacy.stock.modules.supply.entity;

import com.pharmacy.stock.modules.product.entity.Product;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "supply_request_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupplyRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supply_request_id", nullable = false)
    private SupplyRequest supplyRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity_requested", nullable = false)
    private Integer quantityRequested;

    /** Quantité accordée par l'entité sollicitée. */
    @Column(name = "quantity_approved")
    private Integer quantityApproved;

    /** Quantité effectivement livrée à l'entité demandeuse. */
    @Column(name = "quantity_delivered")
    private Integer quantityDelivered = 0;
}
