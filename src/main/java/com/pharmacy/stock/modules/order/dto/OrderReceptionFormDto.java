package com.pharmacy.stock.modules.order.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lignes de réception saisies par le Dépôt Central (réception partielle ou complète).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderReceptionFormDto extends BaseDto {

    private java.util.List<OrderReceptionLineDto> receptions = new java.util.ArrayList<>();
}
