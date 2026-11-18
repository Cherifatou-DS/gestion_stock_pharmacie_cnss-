package com.pharmacy.stock.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Politique de mot de passe fort : au moins 10 caracteres, avec majuscule,
 * minuscule, chiffre et caractere special.
 *
 * Une valeur nulle ou vide est consideree valide par cette contrainte :
 * la presence du mot de passe est geree par @NotBlank ou par le service
 * (mot de passe optionnel lors de la modification d'un utilisateur).
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "Le mot de passe doit contenir au moins 10 caractères, "
            + "avec au moins une majuscule, une minuscule, un chiffre et un caractère spécial";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
