package com.pharmacy.stock.modules.user.repository;

import com.pharmacy.stock.modules.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByIsActiveTrue();

    /**
     * Utilisateurs actifs rattachés à une structure (contrôle avant désactivation).
     */
    long countByEntityIdAndIsActiveTrue(Long entityId);

    /**
     * Administrateurs actifs autres que celui désigné (contrôle "dernier admin").
     */
    long countByRoleNameAndIsActiveTrueAndIdNot(String roleName, Long id);
}
