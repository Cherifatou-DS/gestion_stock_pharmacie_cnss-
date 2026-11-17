package com.pharmacy.stock.modules.order.entity;

/**
 * Statuts du circuit de validation des commandes (colonne status de la table orders).
 */
public enum OrderStatus {

    BROUILLON("Brouillon", "bg-secondary"),
    EN_ATTENTE_SERVIE("En attente de service (PC)", "bg-blue"),
    EN_ATTENTE_DBI("En attente de validation DBI", "bg-danger"),
    AJUSTEE_DBI("Ajustée par la DBI", "bg-warning text-dark"),
    VALIDEE_DBI("Validée par la DBI", "bg-success"),
    EXPEDIEE("Expédiée", "bg-info text-dark"),
    LIVREE("Livrée", "bg-success"),
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
