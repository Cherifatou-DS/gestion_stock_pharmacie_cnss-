package com.pharmacy.stock.modules.user.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Représentation en lecture d'un utilisateur (sans le hash du mot de passe).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDto extends BaseDto {

    private Long id;

    private String username;

    private String firstName;

    private String lastName;

    private String fullName;

    private String email;

    private Long roleId;

    private String roleName;

    private Long entityId;

    private String entityName;

    private String entityCode;

    private Boolean isActive;

    private LocalDateTime createdAt;
}
