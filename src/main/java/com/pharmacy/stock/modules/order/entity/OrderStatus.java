package com.pharmacy.stock.modules.order.entity;

/**
 * Statuts du circuit des commandes fournisseurs passees par la DAAS
 * (colonne status de la table orders).
 *
 * Circuit : BROUILLON -> EN_ATTENTE_DBI -> (AJUSTEE_DBI) -> VALIDEE_DBI
 *           -> ENVOYEE_FOURNISSEUR -> (RECUE_PARTIELLEMENT) -> RECUE.
 * La validation DBI est systematique ; seul le Depot Central receptionne.
 */
public enum OrderStatus {

    BROUILLON("Brouillon", "bg-secondary"),
    EN_ATTENTE_DBI("En attente de validation DBI", "bg-danger"),
    AJUSTEE_DBI("Ajustée par la DBI", "bg-warning text-dark"),
    VALIDEE_DBI("Validée par la DBI", "bg-success"),
    ENVOYEE_FOURNISSEUR("Envoyée au fournisseur", "bg-info text-dark"),
    RECUE_PARTIELLEMENT("Reçue partiellement", "bg-warning text-dark"),
    RECUE("Reçue", "bg-success"),
    REJETEE("Rejetée", "bg-danger");

    private final String label;
    private final String badgeClass;

    OrderStatus(String label, String badgeClass) {
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
    public static OrderStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OrderStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
