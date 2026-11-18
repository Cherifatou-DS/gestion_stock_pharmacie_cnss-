package com.pharmacy.stock.modules.entity.service;

import com.pharmacy.stock.common.exception.BusinessException;
import com.pharmacy.stock.modules.entity.dto.EntityFormDto;
import com.pharmacy.stock.modules.entity.entity.StructureEntity;
import com.pharmacy.stock.modules.entity.repository.StructureEntityRepository;
import com.pharmacy.stock.modules.stock.repository.StockRepository;
import com.pharmacy.stock.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StructureEntityServiceImpl implements StructureEntityService {

    private final StructureEntityRepository structureEntityRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;

    @Override
    @Transactional(readOnly = true)
    public List<StructureEntity> findAll() {
        return structureEntityRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StructureEntity> findAllActive() {
        return structureEntityRepository.findAllActive();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StructureEntity> findByType(String type) {
        return structureEntityRepository.findByType(type);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StructureEntity> findById(Long id) {
        return structureEntityRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StructureEntity> findByCode(String code) {
        return structureEntityRepository.findByCode(code);
    }

    @Override
    @Transactional
    public StructureEntity createEntity(EntityFormDto form) {
        structureEntityRepository.findByCodeIgnoreCase(form.getCode())
                .ifPresent(existing -> {
                    throw new BusinessException("Une structure existe déjà avec le code : " + existing.getCode());
                });
        String type = form.getType();
        if (!ALLOWED_TYPES.contains(type)) {
            throw new BusinessException("Type de structure invalide : " + type);
        }
        StructureEntity entity = new StructureEntity();
        entity.setCode(form.getCode().trim().toUpperCase());
        entity.setName(form.getName().trim());
        entity.setType(type);
        return structureEntityRepository.save(entity);
    }

    @Override
    @Transactional
    public StructureEntity updateEntity(Long id, EntityFormDto form) {
        StructureEntity entity = structureEntityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Structure introuvable : id=" + id));

        String code = form.getCode().trim().toUpperCase();
        if (structureEntityRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new BusinessException("Une autre structure utilise déjà le code : " + code);
        }
        String type = form.getType();
        if (!ALLOWED_TYPES.contains(type)) {
            throw new BusinessException("Type de structure invalide : " + type);
        }
        entity.setCode(code);
        entity.setName(form.getName().trim());
        entity.setType(type);
        return structureEntityRepository.save(entity);
    }

    @Override
    @Transactional
    public StructureEntity deactivateEntity(Long id) {
        StructureEntity entity = structureEntityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Structure introuvable : id=" + id));

        if (!Boolean.TRUE.equals(entity.getIsActive())) {
            throw new BusinessException("La structure " + entity.getCode() + " est déjà désactivée.");
        }
        if (PROTECTED_TYPES.contains(entity.getType())) {
            throw new BusinessException("La structure " + entity.getCode()
                    + " est une référence du circuit de distribution et ne peut pas être désactivée.");
        }
        if (userRepository.countByEntityIdAndIsActiveTrue(id) > 0) {
            throw new BusinessException("Des utilisateurs actifs sont rattachés à la structure "
                    + entity.getCode() + ".");
        }
        if (stockRepository.existsByEntityIdAndQuantityGreaterThan(id, 0)) {
            throw new BusinessException("L'inventaire de la structure " + entity.getCode() + " n'est pas vide.");
        }
        entity.setIsActive(false);
        return structureEntityRepository.save(entity);
    }

    @Override
    @Transactional
    public StructureEntity reactivateEntity(Long id) {
        StructureEntity entity = structureEntityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Structure introuvable : id=" + id));

        if (Boolean.TRUE.equals(entity.getIsActive())) {
            throw new BusinessException("La structure " + entity.getCode() + " est déjà active.");
        }
        entity.setIsActive(true);
        return structureEntityRepository.save(entity);
    }

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "CENTRE_MEDICAL", "PHARMACIE_CENTRALE", "DEPOT_CENTRAL", "DBI", "DAAS", "FOURNISSEUR");

    /**
     * Types du circuit de distribution (CM, PC, DC, DBI, DAAS) : structures de
     * référence non désactivables. Seuls les fournisseurs peuvent être désactivés.
     */
    private static final Set<String> PROTECTED_TYPES = Set.of(
            "CENTRE_MEDICAL", "PHARMACIE_CENTRALE", "DEPOT_CENTRAL", "DBI", "DAAS");
}
