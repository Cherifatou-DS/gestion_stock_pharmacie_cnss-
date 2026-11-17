package com.pharmacy.stock.modules.entity.repository;

import com.pharmacy.stock.modules.entity.entity.StructureEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StructureEntityRepository extends JpaRepository<StructureEntity, Long> {
    Optional<StructureEntity> findByCode(String code);

    Optional<StructureEntity> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    List<StructureEntity> findByType(String type);

    /**
     * Structures actives uniquement (listes de sélection, cibles de commande).
     */
    @Query("""
            select e from StructureEntity e
            where e.isActive = true
            order by e.code asc
            """)
    List<StructureEntity> findAllActive();
}
