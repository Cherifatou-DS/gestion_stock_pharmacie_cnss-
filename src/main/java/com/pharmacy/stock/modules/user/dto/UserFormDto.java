package com.pharmacy.stock.modules.user.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Formulaire de création / modification d'un utilisateur.
 * Le mot de passe est obligatoire à la création, optionnel à la modification.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserFormDto extends BaseDto {

    @NotBlank(message = "L'identifiant de connexion est obligatoire")
    @Size(max = 50, message = "50 caractères maximum")
    private String username;

    @Size(max = 50, message = "50 caractères maximum")
    private String firstName;

    @Size(max = 50, message = "50 caractères maximum")
    private String lastName;

    @Email(message = "Adresse e-mail invalide")
    @Size(max = 100, message = "100 caractères maximum")
    private String email;

    @Size(min = 8, max = 100, message = "Le mot de passe doit contenir entre 8 et 100 caractères")
    private String password;

    @jakarta.validation.constraints.NotNull(message = "Le rôle est obligatoire")
    private Long roleId;

    @jakarta.validation.constraints.NotNull(message = "La structure de rattachement est obligatoire")
    private Long entityId;

    private Boolean isActive;
}
