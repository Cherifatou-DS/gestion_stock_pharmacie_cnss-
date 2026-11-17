package com.pharmacy.stock.modules.user.service;

import com.pharmacy.stock.common.exception.BusinessException;
import com.pharmacy.stock.modules.entity.entity.StructureEntity;
import com.pharmacy.stock.modules.entity.repository.StructureEntityRepository;
import com.pharmacy.stock.modules.entity.entity.StructureEntity;
import com.pharmacy.stock.modules.user.dto.UserDto;
import com.pharmacy.stock.modules.user.dto.UserFormDto;
import com.pharmacy.stock.modules.user.entity.Role;
import com.pharmacy.stock.modules.user.entity.User;
import com.pharmacy.stock.modules.user.repository.RoleRepository;
import com.pharmacy.stock.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StructureEntityRepository structureEntityRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> findAll() {
        return userRepository.findAll().stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDto> findById(Long id) {
        return userRepository.findById(id).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDto> findByUsername(String username) {
        return userRepository.findByUsername(username).map(this::toDto);
    }

    @Override
    @Transactional
    public UserDto createUser(UserFormDto form) {
        if (userRepository.existsByUsername(form.getUsername())) {
            throw new BusinessException("Un utilisateur existe déjà avec l'identifiant : " + form.getUsername());
        }
        User user = new User();
        applyForm(user, form);
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        User saved = userRepository.save(user);
        return toDto(saved);
    }

    @Override
    @Transactional
    public UserDto updateUser(Long id, UserFormDto form) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Utilisateur introuvable : id=" + id));

        userRepository.findByUsername(form.getUsername())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new BusinessException("Un autre utilisateur utilise déjà l'identifiant : " + form.getUsername());
                });

        applyForm(user, form);

        if (StringUtils.hasText(form.getPassword())) {
            user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        }

        return toDto(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserDto toggleActive(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Utilisateur introuvable : id=" + id));
        user.setIsActive(!Boolean.TRUE.equals(user.getIsActive()));
        return toDto(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public long countAll() {
        return userRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long countActive() {
        return userRepository.countByIsActiveTrue();
    }

    private void applyForm(User user, UserFormDto form) {
        user.setUsername(form.getUsername());
        user.setFirstName(form.getFirstName());
        user.setLastName(form.getLastName());
        user.setEmail(form.getEmail());
        user.setIsActive(form.getIsActive() == null || form.getIsActive());
        user.setRole(resolveRole(form.getRoleId()));
        user.setEntity(resolveEntity(form.getEntityId()));
    }

    private Role resolveRole(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException("Rôle introuvable : id=" + roleId));
    }

    private StructureEntity resolveEntity(Long entityId) {
        return structureEntityRepository.findById(entityId)
                .orElseThrow(() -> new BusinessException("Structure introuvable : id=" + entityId));
    }

    private UserDto toDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setFullName((user.getFirstName() + " " + user.getLastName()).trim());
        dto.setEmail(user.getEmail());
        dto.setRoleId(user.getRole().getId());
        dto.setRoleName(user.getRole().getName());
        dto.setEntityId(user.getEntity().getId());
        dto.setEntityName(user.getEntity().getName());
        dto.setEntityCode(user.getEntity().getCode());
        dto.setIsActive(user.getIsActive());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }
}
