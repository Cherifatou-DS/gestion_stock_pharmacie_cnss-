package com.pharmacy.stock.modules.user.service;

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

    UserDto toggleActive(Long id);

    boolean existsByUsername(String username);

    long countAll();

    long countActive();
}
