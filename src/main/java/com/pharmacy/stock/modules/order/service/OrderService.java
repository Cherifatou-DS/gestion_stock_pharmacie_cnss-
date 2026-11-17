package com.pharmacy.stock.modules.order.service;

import com.pharmacy.stock.modules.order.dto.OrderAdjustmentFormDto;
import com.pharmacy.stock.modules.order.dto.OrderDto;
import com.pharmacy.stock.modules.order.dto.OrderFormDto;

import java.util.List;
import java.util.Optional;

public interface OrderService {

    /**
     * Création d'une demande / commande à l'état BROUILLON.
     */
    OrderDto createOrder(OrderFormDto form, Long userId, String userRole, Long userEntityId);

    /**
     * Soumission de la commande : circuit direct (PC) ou validation DBI.
     */
    OrderDto submitOrder(Long orderId, Long userId, String userRole);

    /**
     * Processus A : la Pharmacie Centrale valide et délivre directement (débit du stock PC).
     */
    OrderDto deliverOrder(Long orderId, Long userId, String userRole);

    /**
     * Rejet de la commande (par la PC pour une demande directe, par la DBI sinon).
     */
    OrderDto rejectOrder(Long orderId, Long userId, String userRole);

    /**
     * Processus B : réajustement des quantités par la DBI.
     */
    OrderDto adjustOrder(Long orderId, OrderAdjustmentFormDto form, Long userId, String userRole);

    /**
     * Processus B : validation par la DBI.
     */
    OrderDto validateByDbi(Long orderId, Long userId, String userRole);

    /**
     * Processus B : expédition par le Dépôt Central (débit du stock DC).
     */
    OrderDto shipOrder(Long orderId, Long userId, String userRole);

    /**
     * Réception de l'expédition par la structure destinataire (crédit du stock).
     */
    OrderDto receiveOrder(Long orderId, Long userId, String userRole, Long userEntityId);

    /**
     * Commande Fournisseur : réception des produits au Dépôt Central (crédit du stock DC).
     */
    OrderDto receiveSupplierDelivery(Long orderId, Long userId, String userRole);

    List<OrderDto> findOrders(Long userEntityId, String userRole, Long sourceEntityId, String status);

    Optional<OrderDto> findOrderDetail(Long orderId, Long userEntityId, String userRole);
}
