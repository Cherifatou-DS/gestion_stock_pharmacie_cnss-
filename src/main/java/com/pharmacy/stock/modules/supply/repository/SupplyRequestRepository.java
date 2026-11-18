package com.pharmacy.stock.modules.supply.repository;

import com.pharmacy.stock.modules.supply.entity.SupplyRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplyRequestRepository extends JpaRepository<SupplyRequest, Long> {

    Optional<SupplyRequest> findByRequestNumber(String requestNumber);

    /**
     * Nombre de demandes dans un ensemble de statuts (tableau de bord).
     */
    long countByStatusIn(List<String> statuses);

    /**
     * Dernier numéro de demande généré pour une année donnée (ex : DEM-2026-0003).
     */
    @Query("select max(sr.requestNumber) from SupplyRequest sr where sr.requestNumber like concat('DEM-', :year, '-%')")
    String findLastRequestNumberOfYear(@Param("year") String year);

    /**
     * Liste des demandes d'approvisionnement avec entités et auteur chargés,
     * du plus récent au plus ancien. Les filtres null sont ignorés.
     */
    @Query("""
            select sr from SupplyRequest sr
            join fetch sr.requestingEntity re
            join fetch sr.supplyingEntity se
            join fetch sr.createdByUser u
            where (:requestingEntityId is null or re.id = :requestingEntityId)
              and (:supplyingEntityId is null or se.id = :supplyingEntityId)
              and (:status is null or sr.status = :status)
            order by sr.createdAt desc, sr.id desc
            """)
    List<SupplyRequest> search(@Param("requestingEntityId") Long requestingEntityId,
                               @Param("supplyingEntityId") Long supplyingEntityId,
                               @Param("status") String status);

    /**
     * Demandes DC → DAAS ouvertes et sans commande fournisseur liée,
     * à traiter par la DAAS.
     */
    @Query("""
            select sr from SupplyRequest sr
            join fetch sr.requestingEntity re
            join fetch sr.supplyingEntity se
            join fetch sr.createdByUser u
            where se.type = 'DAAS'
              and sr.status in ('SOUMISE', 'EN_COURS_TRAITEMENT')
              and not exists (select 1 from com.pharmacy.stock.modules.order.entity.Order o
                              where o.supplyRequest = sr)
            order by sr.createdAt desc, sr.id desc
            """)
    List<SupplyRequest> findOpenRequestsForDaas();

    /**
     * Demande complète avec lignes et produits chargés.
     */
    @Query("""
            select distinct sr from SupplyRequest sr
            join fetch sr.requestingEntity re
            join fetch sr.supplyingEntity se
            join fetch sr.createdByUser u
            left join fetch sr.processedByUser pu
            left join fetch sr.requestItems i
            join fetch i.product p
            where sr.id = :id
            """)
    Optional<SupplyRequest> findByIdWithDetails(@Param("id") Long id);
}
