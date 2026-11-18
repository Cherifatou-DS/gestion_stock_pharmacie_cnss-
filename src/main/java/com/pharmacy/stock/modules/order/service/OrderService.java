package com.pharmacy.stock.modules.order.service;

import com.pharmacy.stock.modules.order.dto.OrderAdjustmentFormDto;
import com.pharmacy.stock.modules.order.dto.OrderDto;
import com.pharmacy.stock.modules.order.dto.OrderFormDto;
import com.pharmacy.stock.modules.order.dto.OrderReceptionFormDto;

import java.util.List;
import java.util.Optional;

/**
 * Circuit des commandes fournisseurs (v1.2) : seul l'acteur DAAS crée et
 * transmet des commandes, la DBI valide/ajuste/rejette systématiquement,
 * le Dépôt Central réceptionne les marchandises.
 */
public interface OrderService {

    /**
     * Création d'une commande fournisseur à l'état BROUILLON (rôle DAAS),
     * éventuellement en réponse à une demande d'approvisionnement DC → DAAS.
     */
    OrderDto createOrder(OrderFormDto form, Long userId, String userRole, Long userEntityId);

    /**
     * Soumission DAAS : BROUILLON → EN_ATTENTE_DBI (validation DBI systématique).
     */
    OrderDto submitOrder(Long orderId, Long userId, String userRole);

    /**
     * Réajustement des quantités par la DBI : EN_ATTENTE_DBI → AJUSTEE_DBI.
     */
    OrderDto adjustOrder(Long orderId, OrderAdjustmentFormDto form, Long userId, String userRole);

    /**
     * Validation par la DBI : EN_ATTENTE_DBI / AJUSTEE_DBI → VALIDEE_DBI.
     * Enregistre l'engagement financier fournisseur correspondant.
     */
    OrderDto validateByDbi(Long orderId, Long userId, String userRole);

    /**
     * Rejet par la DBI (motif obligatoire).
     */
    OrderDto rejectOrder(Long orderId, String rejectionReason, Long userId, String userRole);

    /**
     * Transmission au fournisseur par la DAAS : VALIDEE_DBI → ENVOYEE_FOURNISSEUR.
     */
    OrderDto sendToSupplier(Long orderId, Long userId, String userRole);

    /**
     * Réception des marchandises au Dépôt Central : ENVOYEE_FOURNISSEUR /
     * RECUE_PARTIELLEMENT → RECUE_PARTIELLEMENT ou RECUE. Génère les mouvements
     * ENTREE au DC (liés à la commande) et met à jour la demande d'approvisionnement
     * liée le cas échéant.
     */
    OrderDto receiveOrder(Long orderId, OrderReceptionFormDto form, Long userId, String userRole,
                          Long userEntityId);

    List<OrderDto> findOrders(Long userEntityId, String userRole, Long supplierEntityId, String status);

    Optional<OrderDto> findOrderDetail(Long orderId, Long userEntityId, String userRole);
}
