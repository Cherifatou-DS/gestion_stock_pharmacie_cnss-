package com.pharmacy.stock.modules.supply.service;

import com.pharmacy.stock.modules.order.entity.Order;
import com.pharmacy.stock.modules.supply.dto.SupplyRequestDto;
import com.pharmacy.stock.modules.supply.dto.SupplyRequestFormDto;
import com.pharmacy.stock.modules.supply.dto.SupplyRequestProcessFormDto;

import java.util.List;
import java.util.Optional;

public interface SupplyRequestService {

    /**
     * Création d'une demande d'approvisionnement à l'état BROUILLON.
     * Relations autorisées : CM → PC, PC → DC, DC → DAAS.
     */
    SupplyRequestDto createRequest(SupplyRequestFormDto form, Long userId, String userRole, Long userEntityId);

    /**
     * Soumission de la demande à l'entité sollicitée : BROUILLON → SOUMISE.
     */
    SupplyRequestDto submitRequest(Long requestId, Long userId, String userRole);

    /**
     * Rejet de la demande par l'entité sollicitée : SOUMISE → REJETEE (motif obligatoire).
     */
    SupplyRequestDto rejectRequest(Long requestId, String rejectionReason, Long userId, String userRole);

    /**
     * Prise en charge par l'entité sollicitée : SOUMISE → EN_COURS_TRAITEMENT.
     */
    SupplyRequestDto takeCharge(Long requestId, Long userId, String userRole);

    /**
     * Expédition par l'entité sollicitée (CM → PC, PC → DC uniquement) :
     * enregistre les quantités accordées, débite son stock (mouvement SORTIE lié
     * à la demande) et valorise la sortie (transaction financière).
     * EN_COURS_TRAITEMENT (ou SOUMISE) → EXPEDIEE.
     */
    SupplyRequestDto shipRequest(Long requestId, SupplyRequestProcessFormDto form,
                                 Long userId, String userRole);

    /**
     * Confirmation de la livraison par l'entité demandeuse (CM → PC, PC → DC) :
     * EXPEDIEE → LIVREE, crédit du stock de la structure demandeuse.
     */
    SupplyRequestDto confirmDelivery(Long requestId, Long userId, String userRole, Long userEntityId);

    /**
     * Circuit DC → DAAS : la réception au Dépôt Central d'une commande liée met
     * à jour les quantités livrées ; la demande passe à LIVREE lorsque la
     * réception couvre les quantités demandées.
     */
    SupplyRequestDto registerOrderDelivery(Order order);

    /**
     * Passage en cours de traitement par la DAAS lors de l'émission d'une commande
     * fournisseur en réponse à la demande.
     */
    void markProcessingByDaas(Long requestId, Long daasUserId);

    /**
     * Demandes d'approvisionnement DC → DAAS ouvertes (SOUMISE ou EN_COURS_TRAITEMENT)
     * n'ayant pas encore de commande fournisseur liée.
     */
    List<SupplyRequestDto> findOpenRequestsForDaas();

    List<SupplyRequestDto> findRequests(Long userEntityId, String userRole,
                                        Long requestingEntityId, String status);

    Optional<SupplyRequestDto> findRequestDetail(Long requestId, Long userEntityId, String userRole);
}
