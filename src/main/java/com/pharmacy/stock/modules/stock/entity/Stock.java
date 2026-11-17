package com.pharmacy.stock.modules.stock.entity;

import com.pharmacy.stock.modules.entity.entity.StructureEntity;
import com.pharmacy.stock.modules.product.entity.Product;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "stocks", uniqueConstraints = {
    @UniqueConstraint(name = "uk_entity_product", columnNames = {"entity_id", "product_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id", nullable = false)
    private StructureEntity entity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity = 0;

    @Column(name = "min_threshold")
    private Integer minThreshold = 10;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
