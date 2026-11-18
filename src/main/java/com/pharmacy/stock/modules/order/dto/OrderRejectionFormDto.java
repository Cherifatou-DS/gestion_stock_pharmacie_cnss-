package com.pharmacy.stock.modules.order.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Formulaire de rejet d'une commande par la DBI (motif obligatoire).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderRejectionFormDto extends BaseDto {

    @NotBlank(message = "Le motif du rejet est obligatoire")
    private String rejectionReason;
}
