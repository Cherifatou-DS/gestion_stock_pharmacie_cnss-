package com.pharmacy.stock.modules.user.dto;

import com.pharmacy.stock.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Formulaire de changement de mot de passe par l'utilisateur lui-meme
 * (changement force a la premiere connexion ou a la demande).
 */
@Getter
@Setter
@NoArgsConstructor
public class ChangePasswordFormDto {

    @NotBlank(message = "Le mot de passe actuel est obligatoire")
    private String currentPassword;

    @NotBlank(message = "Le nouveau mot de passe est obligatoire")
    @StrongPassword
    private String newPassword;

    @NotBlank(message = "La confirmation du nouveau mot de passe est obligatoire")
    private String confirmNewPassword;
}
