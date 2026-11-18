package com.pharmacy.stock.modules.order.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Commande fournisseur en lecture (passée par la DAAS, validée par la DBI,
 * réceptionnée par le Dépôt Central).
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

    private Long supplierEntityId;

    private String supplierEntityCode;

    private String supplierEntityName;

    private Long receivingEntityId;

    private String receivingEntityCode;

    private String receivingEntityName;

    private Long supplyRequestId;

    private String supplyRequestNumber;

    private String status;

    private BigDecimal totalAmount;

    private String rejectionReason;

    private String createdByName;

    private String validatedByName;

    private LocalDateTime validatedAt;

    private LocalDateTime sentAt;

    private String receivedByName;

    private LocalDateTime receivedAt;

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
