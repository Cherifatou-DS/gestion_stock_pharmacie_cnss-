package com.pharmacy.stock.modules.order.dto;

import com.pharmacy.stock.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Formulaire de réajustement des quantités d'une commande par la DBI.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderAdjustmentFormDto extends BaseDto {

    private List<OrderAdjustmentLineDto> adjustments = new ArrayList<>();
}
