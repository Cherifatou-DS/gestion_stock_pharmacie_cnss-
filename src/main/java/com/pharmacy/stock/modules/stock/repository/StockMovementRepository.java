package com.pharmacy.stock.modules.stock.repository;

import com.pharmacy.stock.modules.stock.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /**
     * Recherche multi-critères du journal des mouvements, du plus récent au plus ancien.
     * Les filtres null sont ignorés.
     */
    @Query("""
            select m from StockMovement m
            join fetch m.entity e
            join fetch m.product p
            join fetch m.createdByUser u
            where (:entityId is null or e.id = :entityId)
              and (:productId is null or p.id = :productId)
              and (:type is null or m.type = :type)
            order by m.createdAt desc, m.id desc
            """)
    List<StockMovement> search(@Param("entityId") Long entityId,
                               @Param("productId") Long productId,
                               @Param("type") String type);
}
