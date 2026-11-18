package com.pharmacy.stock.modules.supply.entity;

/**
 * Statuts du circuit des demandes d'approvisionnement internes
 * (colonne status de la table supply_requests), applicables aux trois
 * relations CM -> PC, PC -> DC et DC -> DAAS.
 *
 * Circuit : BROUILLON -> SOUMISE -> (REJETEE | EN_COURS_TRAITEMENT)
 *           -> EXPEDIEE -> LIVREE.
 * Pour DC -> DAAS, la demande ne mouvemente pas directement de stock :
 * la DAAS la traite en emettant une commande fournisseur et la demande
 * passe a LIVREE lorsque la reception au Depot Central couvre les quantites.
 */
public enum SupplyRequestStatus {

    BROUILLON("Brouillon", "bg-secondary"),
    SOUMISE("Soumise", "bg-blue"),
    EN_COURS_TRAITEMENT("En cours de traitement", "bg-info text-dark"),
    EXPEDIEE("Expédiée", "bg-warning text-dark"),
    LIVREE("Livrée", "bg-success"),
    REJETEE("Rejetée", "bg-danger");

    private final String label;
    private final String badgeClass;

    SupplyRequestStatus(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }

    public String getLabel() {
        return label;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    /**
     * Conversion sûre depuis une valeur de formulaire ou de base.
     */
    public static SupplyRequestStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        try {
            return SupplyRequestStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
