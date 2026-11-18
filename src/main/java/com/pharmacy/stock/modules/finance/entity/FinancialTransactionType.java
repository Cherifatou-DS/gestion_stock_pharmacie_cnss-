package com.pharmacy.stock.modules.finance.entity;

/**
 * Types de transactions financières suivies par la DBI
 * (colonne transaction_type de la table financial_transactions).
 */
public enum FinancialTransactionType {

    /** Engagement financier auprès du fournisseur, enregistré à la validation d'une commande. */
    ENGAGEMENT_FOURNISSEUR("Engagement fournisseur", "bg-blue"),

    /** Sortie de stock valorisée, enregistrée à l'expédition d'une demande d'approvisionnement. */
    SORTIE_STOCK_VALORISEE("Sortie de stock valorisée", "bg-danger");

    private final String label;
    private final String badgeClass;

    FinancialTransactionType(String label, String badgeClass) {
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
    public static FinancialTransactionType fromValue(String value) {
        if (value == null) {
            return null;
        }
        try {
            return FinancialTransactionType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
