package com.pharmacy.stock.modules.user.service;

import com.pharmacy.stock.modules.user.dto.ChangePasswordFormDto;
import com.pharmacy.stock.modules.user.dto.UserDto;
import com.pharmacy.stock.modules.user.dto.UserFormDto;

import java.util.List;
import java.util.Optional;

public interface UserService {

    List<UserDto> findAll();

    Optional<UserDto> findById(Long id);

    Optional<UserDto> findByUsername(String username);

    UserDto createUser(UserFormDto form);

    UserDto updateUser(Long id, UserFormDto form);

    /**
     * @param currentUserId identifiant de l'administrateur connecte (garde-fous
     *                      auto-desactivation et dernier administrateur).
     */
    UserDto toggleActive(Long id, Long currentUserId);

    /**
     * Changement de mot de passe par l'utilisateur lui-meme
     * (verification du mot de passe actuel, levee du drapeau must_change_password).
     */
    void changePassword(Long userId, ChangePasswordFormDto form);

    boolean existsByUsername(String username);

    long countAll();

    long countActive();
}
