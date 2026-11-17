package com.pharmacy.stock.modules.entity.service;

import com.pharmacy.stock.modules.entity.entity.StructureEntity;

import java.util.List;
import java.util.Optional;

public interface StructureEntityService {

    List<StructureEntity> findAll();

    List<StructureEntity> findAllActive();

    List<StructureEntity> findByType(String type);

    Optional<StructureEntity> findById(Long id);

    Optional<StructureEntity> findByCode(String code);

    /**
     * Création d'une nouvelle structure de référence par l'administrateur.
     */
    StructureEntity createEntity(com.pharmacy.stock.modules.entity.dto.EntityFormDto form);

    /**
     * Modification d'une structure existante (code, nom, type).
     */
    StructureEntity updateEntity(Long id, com.pharmacy.stock.modules.entity.dto.EntityFormDto form);

    /**
     * Suppression logique : refuse si la structure est une référence du circuit,
     * si des utilisateurs actifs y sont rattachés ou si son inventaire n'est pas vide.
     */
    StructureEntity deactivateEntity(Long id);

    /**
     * Réactivation d'une structure précédemment désactivée.
     */
    StructureEntity reactivateEntity(Long id);
}
