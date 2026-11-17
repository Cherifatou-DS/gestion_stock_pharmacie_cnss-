package com.pharmacy.stock.it;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration du circuit complet des commandes
 * (processus A : livraison directe PC ; processus B : circuit DBI).
 */
class OrderWorkflowIT extends AbstractIntegrationTest {

    private static final Long PC_ID = 1L;
    private static final Long DC_ID = 2L;
    private static final Long MED001_ID = 1L;

    @Test
    void circuit_complet_pc_dci_avec_reajustement_et_validation_dbi() throws Exception {
        // Stock de demonstration DC / MED001 = 500 (V5)
        MockHttpSession pharmacie = login("resp_pharmacie", DEMO_PASSWORD);

        Long orderId = createOrder(pharmacie, DC_ID, 40);
        mockMvc.perform(post("/orders/{id}/submit", orderId).session(pharmacie).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(statusOf(orderId)).isEqualTo("EN_ATTENTE_DBI");

        // DBI : reajustement 40 -> 30 puis validation
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

        // Depot Central : expedition (stock DC 500 -> 470)
        MockHttpSession depot = login("resp_depot", DEMO_PASSWORD);
        mockMvc.perform(post("/orders/{id}/ship", orderId).session(depot).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(statusOf(orderId)).isEqualTo("EXPEDIEE");
        assertThat(stockOf(DC_ID, MED001_ID)).isEqualTo(470);

        // Pharmacie Centrale : reception (stock PC 250 -> 280)
        mockMvc.perform(post("/orders/{id}/receive", orderId).session(pharmacie).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(statusOf(orderId)).isEqualTo("LIVREE");
        assertThat(stockOf(PC_ID, MED001_ID)).isEqualTo(280);

        Integer delivered = jdbcTemplate.queryForObject(
                "SELECT quantity_delivered FROM order_items WHERE order_id = ?", Integer.class, orderId);
        assertThat(delivered).isEqualTo(30);
    }

    @Test
    void un_chef_de_service_ne_peut_pas_valider_une_commande_dbi() throws Exception {
        MockHttpSession pharmacie = login("resp_pharmacie", DEMO_PASSWORD);
        Long orderId = createOrder(pharmacie, DC_ID, 40);
        mockMvc.perform(post("/orders/{id}/submit", orderId).session(pharmacie).with(csrf()))
                .andExpect(status().is3xxRedirection());

        MockHttpSession chef = login("chef_cm01", DEMO_PASSWORD);
        mockMvc.perform(post("/orders/{id}/validate-dbi", orderId).session(chef).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertThat(statusOf(orderId)).isEqualTo("EN_ATTENTE_DBI");
    }

    @Test
    void expedition_impossible_si_stock_depot_insuffisant() throws Exception {
        // Reduction arbitraire du stock de demonstration DC / MED001 a 5 unites
        jdbcTemplate.update("UPDATE stocks SET quantity = 5 WHERE entity_id = 2 AND product_id = 1");

        MockHttpSession pharmacie = login("resp_pharmacie", DEMO_PASSWORD);
        Long orderId = createOrder(pharmacie, DC_ID, 40);
        mockMvc.perform(post("/orders/{id}/submit", orderId).session(pharmacie).with(csrf()))
                .andExpect(status().is3xxRedirection());

        MockHttpSession dbi = login("dbi_valideur", DEMO_PASSWORD);
        mockMvc.perform(post("/orders/{id}/validate-dbi", orderId).session(dbi).with(csrf()))
                .andExpect(status().is3xxRedirection());

        MockHttpSession depot = login("resp_depot", DEMO_PASSWORD);
        mockMvc.perform(post("/orders/{id}/ship", orderId).session(depot).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertThat(statusOf(orderId)).isEqualTo("VALIDEE_DBI");
    }

    @Test
    void demande_cm_livree_directement_par_la_pc_sans_dbi() throws Exception {
        // Processus A : stock de demonstration PC / MED001 = 250 (V5)
        MockHttpSession chef = login("chef_cm01", DEMO_PASSWORD);
        Long orderId = createOrder(chef, PC_ID, 20);

        mockMvc.perform(post("/orders/{id}/submit", orderId).session(chef).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(statusOf(orderId)).isEqualTo("EN_ATTENTE_SERVIE");
        assertThat(requiresDbi(orderId)).isFalse();

        MockHttpSession pharmacie = login("resp_pharmacie", DEMO_PASSWORD);
        mockMvc.perform(post("/orders/{id}/deliver", orderId).session(pharmacie).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(statusOf(orderId)).isEqualTo("LIVREE");
        // Stock PC : 250 -> 230
        assertThat(stockOf(PC_ID, MED001_ID)).isEqualTo(230);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Long createOrder(MockHttpSession session, Long targetId, int quantity) throws Exception {
        mockMvc.perform(post("/orders").session(session).with(csrf())
                        .param("targetEntityId", targetId.toString())
                        .param("lines[0].productId", MED001_ID.toString())
                        .param("lines[0].quantityRequested", String.valueOf(quantity)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders"));
        Long orderId = jdbcTemplate.queryForObject("SELECT max(id) FROM orders", Long.class);
        assertThat(orderId).isNotNull();
        return orderId;
    }

    private String statusOf(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private boolean requiresDbi(Long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT requires_dbi_validation FROM orders WHERE id = ?", Boolean.class, orderId);
    }

    private Integer stockOf(Long entityId, Long productId) {
        return jdbcTemplate.queryForObject(
                "SELECT quantity FROM stocks WHERE entity_id = ? AND product_id = ?",
                Integer.class, entityId, productId);
    }
}
