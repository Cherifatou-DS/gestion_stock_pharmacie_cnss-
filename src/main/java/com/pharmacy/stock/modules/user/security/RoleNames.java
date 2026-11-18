package com.pharmacy.stock.modules.user.security;

/**
 * Noms de roles et regles d'autorisation partagees entre les modules
 * (controleurs et services). Source de verite unique pour eviter les
 * divergences de controle d'acces.
 */
public final class RoleNames {

    public static final String ADMIN = "ADMIN";
    public static final String RESPONSABLE_PHARMACIE = "RESPONSABLE_PHARMACIE";
    public static final String RESPONSABLE_DEPOT = "RESPONSABLE_DEPOT";
    public static final String MAGASINIER = "MAGASINIER";
    public static final String DAAS = "DAAS";

    private RoleNames() {
    }

    /**
     * Roles autorises a intervenir sur toutes les structures :
     * administrateur, responsables de la Pharmacie Centrale et du Depot
     * Central, magasiniers et agents de la DAAS.
     */
    public static boolean isCrossEntityRole(String role) {
        return ADMIN.equals(role) || RESPONSABLE_PHARMACIE.equals(role)
                || RESPONSABLE_DEPOT.equals(role) || MAGASINIER.equals(role)
                || DAAS.equals(role);
    }

    /**
     * Inverse de {@link #isCrossEntityRole(String)} : les roles de centre
     * medical (CHEF_SERVICE, AGENT_APPROVISIONNEMENT, DBI) ne consultent
     * et n'ecrivent que sur leur propre structure de rattachement.
     */
    public static boolean isRestrictedToOwnEntity(String role) {
        return !isCrossEntityRole(role);
    }
}
