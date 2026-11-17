package com.pharmacy.stock.modules.audit.service;

import com.pharmacy.stock.modules.audit.entity.ActionLog;

import java.util.List;

public interface AuditService {

    void record(Long userId, String actionType, String entityAffected, Long recordId, String details, String ipAddress);

    /**
     * Recherche multi-critères du journal d'audit (filtres null ignorés).
     */
    List<ActionLog> search(Long userId, String actionType, String entityAffected);

    /**
     * Types d'actions distincts journalisés.
     */
    List<String> actionTypes();

    /**
     * Entités affectées distinctes journalisées.
     */
    List<String> entitiesAffected();

    /**
     * Dernières actions journalisées (tableau de bord).
     */
    List<ActionLog> findRecent(int limit);

    long countAll();
}
