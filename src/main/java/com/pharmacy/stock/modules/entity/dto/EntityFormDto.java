package com.pharmacy.stock.modules.entity.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Formulaire de création / modification d'une structure de référence
 * (Centre Médical, Pharmacie Centrale, Dépôt Central, DBI, Fournisseur).
 * L'identifiant est renseigné uniquement en mode modification.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EntityFormDto extends BaseDto {

    private Long id;

    @NotBlank(message = "Le code de la structure est obligatoire")
    @Size(max = 20, message = "20 caractères maximum")
    @Pattern(regexp = "[A-Za-z0-9]+", message = "Le code ne doit contenir que des lettres et des chiffres")
    private String code;

    @NotBlank(message = "Le nom de la structure est obligatoire")
    @Size(max = 100, message = "100 caractères maximum")
    private String name;

    @NotBlank(message = "Le type de structure est obligatoire")
    private String type;
}
