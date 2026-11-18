package com.pharmacy.stock.it;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration du circuit complet des commandes fournisseurs (v1.2) :
 * DAAS (creation) -> DBI (ajustement / validation) -> DAAS (transmission fournisseur)
 * -> Depot Central (reception partielle puis complete).
 */
class OrderWorkflowIT extends AbstractIntegrationTest {

    private static final Long DC_ID = 2L;
    private static final Long FRN01_ID = 6L;
    private static final Long MED001_ID = 1L;

    @Test
    void circuit_complet_daas_dbi_fournisseur_reception_dc() throws Exception {
        MockHttpSession daas = login("agent_daas", DEMO_PASSWORD);

        // Creation DAAS : 40 unites de MED001 (prix unitaire 500) -> BROUILLON
        Long orderId = createOrder(daas, FRN01_ID, 40);
        assertThat(statusOf(orderId)).isEqualTo("BROUILLON");

        // Soumission DAAS -> EN_ATTENTE_DBI
        mockMvc.perform(post("/orders/{id}/submit", orderId).session(daas).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(statusOf(orderId)).isEqualTo("EN_ATTENTE_DBI");

        // DBI : reajustement 40 -> 30 puis validation (engagement 30 x 500 = 15000)
        MockHttpSession dbi = login("dbi_valideur", DEMO_PASSWORD);
        Long itemId = jdbcTemplate.queryForObject(
                "SELECT id FROM order_items WHERE order_id = ?", Long.class, orderId);
        mockMvc.perform(post("/orders/{id}/adjust", orderId).session(dbi).with(csrf())
                        .param("adjustments[0].itemId", itemId.toString())
                        .param("adjustments[0].adjustedQuantity", "30"))
                .andExpect(status().is3xxRedirection());
        assertThat(statusOf(orderId)).isEqualTo("AJUSTEE_DBI");

        mockMvc.perform(post("/orders/{id}/validate-dbi", orderId).session(dbi).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(statusOf(orderId)).isEqualTo("VALIDEE_DBI");
        BigDecimal engagement = jdbcTemplate.queryForObject(
                "SELECT amount FROM financial_transactions WHERE reference_order_id = ?",
                BigDecimal.class, orderId);
        assertThat(engagement).isEqualByComparingTo("15000.00");

        // DAAS : transmission au fournisseur -> ENVOYEE_FOURNISSEUR
        mockMvc.perform(post("/orders/{id}/send", orderId).session(daas).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(statusOf(orderId)).isEqualTo("ENVOYEE_FOURNISSEUR");

        // Depot Central : reception partielle de 20 unites (stock DC 500 -> 520)
        MockHttpSession depot = login("resp_depot", DEMO_PASSWORD);
        mockMvc.perform(post("/orders/{id}/receive", orderId).session(depot).with(csrf())
                        .param("receptions[0].itemId", itemId.toString())
                        .param("receptions[0].receivedQuantity", "20"))
                .andExpect(status().is3xxRedirection());
        assertThat(statusOf(orderId)).isEqualTo("RECUE_PARTIELLEMENT");
        assertThat(stockOf(DC_ID, MED001_ID)).isEqualTo(520);

        // Reception complete des 10 unites restantes (stock DC 520 -> 530)
        mockMvc.perform(post("/orders/{id}/receive", orderId).session(depot).with(csrf())
                        .param("receptions[0].itemId", itemId.toString())
                        .param("receptions[0].receivedQuantity", "10"))
                .andExpect(status().is3xxRedirection());
        assertThat(statusOf(orderId)).isEqualTo("RECUE");
        assertThat(stockOf(DC_ID, MED001_ID)).isEqualTo(530);

        Integer received = jdbcTemplate.queryForObject(
                "SELECT quantity_received FROM order_items WHERE order_id = ?", Integer.class, orderId);
        assertThat(received).isEqualTo(30);

        // Mouvement ENTREE lie a la commande (reference_order_id renseigne)
        String reference = jdbcTemplate.queryForObject(
                "SELECT o.order_number FROM stock_movements m JOIN orders o ON o.id = m.reference_order_id "
                        + "WHERE m.type = 'ENTREE' LIMIT 1", String.class);
        assertThat(reference).isEqualTo(orderNumberOf(orderId));
    }

    @Test
    void une_commande_ne_peut_pas_etre_envoyee_sans_validation_dbi() throws Exception {
        MockHttpSession daas = login("agent_daas", DEMO_PASSWORD);
        Long orderId = createOrder(daas, FRN01_ID, 40);
        mockMvc.perform(post("/orders/{id}/submit", orderId).session(daas).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(statusOf(orderId)).isEqualTo("EN_ATTENTE_DBI");

        // La DAAS ne peut pas transmettre une commande non validee par la DBI.
        mockMvc.perform(post("/orders/{id}/send", orderId).session(daas).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertThat(statusOf(orderId)).isEqualTo("EN_ATTENTE_DBI");
    }

    @Test
    void rejet_dbi_avec_motif_obligatoire() throws Exception {
        MockHttpSession daas = login("agent_daas", DEMO_PASSWORD);
        Long orderId = createOrder(daas, FRN01_ID, 40);
        mockMvc.perform(post("/orders/{id}/submit", orderId).session(daas).with(csrf()))
                .andExpect(status().is3xxRedirection());

        MockHttpSession dbi = login("dbi_valideur", DEMO_PASSWORD);
        // Sans motif : le formulaire est re-rendu avec l'erreur de validation
        mockMvc.perform(post("/orders/{id}/reject", orderId).session(dbi).with(csrf()))
                .andExpect(status().isOk());
        assertThat(statusOf(orderId)).isEqualTo("EN_ATTENTE_DBI");

        // Avec motif : rejet enregistre
        mockMvc.perform(post("/orders/{id}/reject", orderId).session(dbi).with(csrf())
                        .param("rejectionReason", "Budget insuffisant"))
                .andExpect(status().is3xxRedirection());
        assertThat(statusOf(orderId)).isEqualTo("REJETEE");
        String reason = jdbcTemplate.queryForObject(
                "SELECT rejection_reason FROM orders WHERE id = ?", String.class, orderId);
        assertThat(reason).isEqualTo("Budget insuffisant");
    }

    @Test
    void seul_un_utilisateur_daas_peut_creer_une_commande() throws Exception {
        // Le responsable PC n'a pas accès au module /orders (403 côté sécurité).
        MockHttpSession pharmacie = login("resp_pharmacie", DEMO_PASSWORD);
        mockMvc.perform(post("/orders").session(pharmacie).with(csrf())
                        .param("supplierEntityId", FRN01_ID.toString())
                        .param("lines[0].productId", MED001_ID.toString())
                        .param("lines[0].quantityRequested", "20"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Long createOrder(MockHttpSession session, Long supplierId, int quantity) throws Exception {
        mockMvc.perform(post("/orders").session(session).with(csrf())
                        .param("supplierEntityId", supplierId.toString())
                        .param("lines[0].productId", MED001_ID.toString())
                        .param("lines[0].quantityRequested", String.valueOf(quantity)))
                .andExpect(status().is3xxRedirection());
        Long orderId = jdbcTemplate.queryForObject("SELECT max(id) FROM orders", Long.class);
        assertThat(orderId).isNotNull();
        return orderId;
    }

    private String orderNumberOf(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT order_number FROM orders WHERE id = ?", String.class, orderId);
    }

    private String statusOf(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private Integer stockOf(Long entityId, Long productId) {
        return jdbcTemplate.queryForObject(
                "SELECT quantity FROM stocks WHERE entity_id = ? AND product_id = ?",
                Integer.class, entityId, productId);
    }
}
