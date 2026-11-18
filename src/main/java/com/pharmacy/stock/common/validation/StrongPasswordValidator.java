package com.pharmacy.stock.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Verificateur de la politique de mot de passe fort associee a @StrongPassword.
 * Longueur minimale 10 caracteres + 4 classes de caracteres obligatoires.
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final int MIN_LENGTH = 10;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        // Absence ou vide : valide ici (presence geree ailleurs), conforme a la JavaDoc de l'annotation.
        if (password == null || password.isBlank()) {
            return true;
        }
        if (password.length() < MIN_LENGTH) {
            return false;
        }
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }
}
