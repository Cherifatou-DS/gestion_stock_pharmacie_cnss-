package com.pharmacy.stock.modules.order.repository;

import com.pharmacy.stock.modules.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * Indique si une commande fournisseur est déjà liée à une demande d'approvisionnement.
     */
    boolean existsBySupplyRequestId(Long supplyRequestId);

    /**
     * Nombre de commandes dans un ensemble de statuts (tableau de bord).
     */
    long countByStatusIn(List<String> statuses);

    long countByStatus(String status);

    /**
     * Dernier numéro de commande généré pour une année donnée (ex : CMD-2026-0007).
     */
    @Query("select max(o.orderNumber) from Order o where o.orderNumber like concat('CMD-', :year, '-%')")
    String findLastOrderNumberOfYear(@Param("year") String year);

    /**
     * Liste des commandes fournisseurs avec entités et auteur chargés,
     * du plus récent au plus ancien. Les filtres null sont ignorés.
     */
    @Query("""
            select o from Order o
            join fetch o.sourceEntity s
            join fetch o.supplierEntity sup
            join fetch o.receivingEntity rec
            join fetch o.createdByUser u
            where (:sourceEntityId is null or s.id = :sourceEntityId)
              and (:status is null or o.status = :status)
            order by o.createdAt desc, o.id desc
            """)
    List<Order> search(@Param("sourceEntityId") Long sourceEntityId,
                       @Param("status") String status);

    /**
     * Commande complète avec lignes et produits chargés.
     */
    @Query("""
            select distinct o from Order o
            join fetch o.sourceEntity s
            join fetch o.supplierEntity sup
            join fetch o.receivingEntity rec
            join fetch o.createdByUser u
            left join fetch o.supplyRequest sr
            left join fetch o.orderItems i
            join fetch i.product p
            where o.id = :id
            """)
    Optional<Order> findByIdWithDetails(@Param("id") Long id);
}
