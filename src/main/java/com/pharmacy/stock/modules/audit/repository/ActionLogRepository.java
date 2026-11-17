package com.pharmacy.stock.modules.audit.repository;

import com.pharmacy.stock.modules.audit.entity.ActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActionLogRepository extends JpaRepository<ActionLog, Long> {

    /**
     * Recherche multi-critères du journal d'audit, de la plus récente à la plus ancienne.
     * Les filtres null sont ignorés.
     */
    @Query("""
            select l from ActionLog l
            join fetch l.user u
            where (:userId is null or u.id = :userId)
              and (:actionType is null or l.actionType = :actionType)
              and (:entityAffected is null or l.entityAffected = :entityAffected)
            order by l.timestamp desc, l.id desc
            """)
    List<ActionLog> search(@Param("userId") Long userId,
                           @Param("actionType") String actionType,
                           @Param("entityAffected") String entityAffected);

    /**
     * Types d'actions distincts déjà journalisés (alimente le filtre).
     */
    @Query("select distinct l.actionType from ActionLog l order by l.actionType")
    List<String> findDistinctActionTypes();

    /**
     * Entités affectées distinctes déjà journalisées (alimente le filtre).
     */
    @Query("select distinct l.entityAffected from ActionLog l order by l.entityAffected")
    List<String> findDistinctEntitiesAffected();
}
