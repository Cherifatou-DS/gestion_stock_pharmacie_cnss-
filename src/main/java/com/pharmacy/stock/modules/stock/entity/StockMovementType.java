package com.pharmacy.stock.modules.stock.entity;

/**
 * Types de mouvements de stock autorisés (colonne type de la table stock_movements).
 */
public enum StockMovementType {

    ENTREE("Entrée"),
    SORTIE("Sortie"),
    AJUSTEMENT_INVENTAIRE("Ajustement d'inventaire");

    private final String label;

    StockMovementType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Conversion sûre depuis une valeur de formulaire.
     */
    public static StockMovementType fromValue(String value) {
        if (value == null) {
            return null;
        }
        try {
            return StockMovementType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
