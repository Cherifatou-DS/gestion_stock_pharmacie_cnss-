package com.pharmacy.stock.modules.supply.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Demande d'approvisionnement en lecture (CM → PC, PC → DC, DC → DAAS).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupplyRequestDto extends BaseDto {

    private Long id;

    private String requestNumber;

    private Long requestingEntityId;

    private String requestingEntityCode;

    private String requestingEntityName;

    private Long supplyingEntityId;

    private String supplyingEntityCode;

    private String supplyingEntityName;

    private String supplyingEntityType;

    private String status;

    private String rejectionReason;

    private String createdByName;

    private String processedByName;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<SupplyRequestItemDto> items;

    /**
     * Indique si la demande relève du circuit DC → DAAS (traitée par commande fournisseur).
     */
    public boolean isDaasCircuit() {
        return "DAAS".equals(supplyingEntityType);
    }

    /**
     * Statut couramment disponible en vue pour les badges / libellés.
     */
    public String getStatusLabel() {
        return com.pharmacy.stock.modules.supply.entity.SupplyRequestStatus.fromValue(status) == null
                ? status
                : com.pharmacy.stock.modules.supply.entity.SupplyRequestStatus.fromValue(status).getLabel();
    }
}
