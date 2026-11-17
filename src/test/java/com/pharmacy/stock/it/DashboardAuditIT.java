package com.pharmacy.stock.it;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Tests d'integration du tableau de bord et du journal d'audit.
 */
class DashboardAuditIT extends AbstractIntegrationTest {

    @Test
    void le_tableau_de_bord_affiche_les_indicateurs_et_les_panneaux() throws Exception {
        var session = loginAdmin();

        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/dashboard/index"))
                .andExpect(model().attributeExists("pendingOrders", "readyToShipOrders",
                        "lowStocks", "productCount", "recentOrders", "recentLogs"));
    }

    @Test
    void le_journal_daudit_est_accesible_avec_filtres_vide() throws Exception {
        var session = loginAdmin();

        mockMvc.perform(get("/audit").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/audit/list"))
                .andExpect(model().attributeExists("logs", "users", "actionTypes", "entitiesAffected"));
    }

    @Test
    void le_filtre_audit_par_utilisateur_ne_plante_pas() throws Exception {
        var session = loginAdmin();

        mockMvc.perform(get("/audit").session(session).param("actionType", "LOGIN_SUCCESS"))
                .andExpect(status().isOk());
    }

    @Test
    void le_tableau_de_bord_est_accesible_aux_autres_roles() throws Exception {
        var session = login("magasinier", DEMO_PASSWORD);

        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk());
    }
}
