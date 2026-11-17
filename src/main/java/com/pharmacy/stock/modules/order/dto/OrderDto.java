package com.pharmacy.stock.modules.order.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * En-tête de commande en lecture (demande ou commande).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto extends BaseDto {

    private Long id;

    private String orderNumber;

    private Long sourceEntityId;

    private String sourceEntityCode;

    private String sourceEntityName;

    private Long targetEntityId;

    private String targetEntityCode;

    private String targetEntityName;

    private String targetEntityType;

    private String status;

    private Boolean requiresDbiValidation;

    private String createdByName;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<OrderItemDto> items;

    /**
     * Statut couramment disponible en vue pour les badges / libellés.
     */
    public String getStatusLabel() {
        return com.pharmacy.stock.modules.order.entity.OrderStatus.fromValue(status) == null
                ? status
                : com.pharmacy.stock.modules.order.entity.OrderStatus.fromValue(status).getLabel();
    }
}
