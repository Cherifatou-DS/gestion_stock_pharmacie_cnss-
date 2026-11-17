package com.pharmacy.stock.modules.stock.repository;

import com.pharmacy.stock.modules.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByEntityIdAndProductId(Long entityId, Long productId);

    /**
     * Toutes les lignes de stock avec entité et produit chargés (évite le N+1).
     */
    @Query("""
            select s from Stock s
            join fetch s.entity e
            join fetch s.product p
            order by e.code asc, p.designation asc
            """)
    List<Stock> findAllWithDetails();

    /**
     * Lignes de stock d'une entité avec entité et produit chargés.
     */
    @Query("""
            select s from Stock s
            join fetch s.entity e
            join fetch s.product p
            where s.entity.id = :entityId
            order by p.designation asc
            """)
    List<Stock> findByEntityWithDetails(@Param("entityId") Long entityId);

    /**
     * Lignes de stock dont la quantité est inférieure ou égale au seuil d'alerte.
     */
    @Query("""
            select s from Stock s
            join fetch s.entity e
            join fetch s.product p
            where s.quantity <= s.minThreshold
            order by e.code asc, p.designation asc
            """)
    List<Stock> findLowStocksWithDetails();

    long countByEntityId(Long entityId);

    /**
     * Indique si la structure possède au moins une ligne de stock non vide
     * (contrôle avant désactivation d'une structure).
     */
    boolean existsByEntityIdAndQuantityGreaterThan(Long entityId, int quantity);
}
