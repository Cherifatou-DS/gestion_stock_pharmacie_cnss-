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
 * Tests d'integration du circuit des demandes d'approvisionnement (v1.2) :
 * CM -> PC (expedition physique avec valorisation) et DC -> DAAS (traitement
 * par commande fournisseur).
 */
class SupplyRequestWorkflowIT extends AbstractIntegrationTest {

    private static final Long PC_ID = 1L;
    private static final Long DC_ID = 2L;
    private static final Long CM01_ID = 4L;
    private static final Long FRN01_ID = 6L;
    private static final Long MED001_ID = 1L;

    @Test
    void circuit_complet_cm_vers_pc_avec_expedition_et_livraison() throws Exception {
        // CM01 cree une demande vers la PC : 20 x MED001
        MockHttpSession chef = login("chef_cm01", DEMO_PASSWORD);
        Long requestId = createRequest(chef, PC_ID, 20);
        assertThat(statusOf(requestId)).isEqualTo("BROUILLON");

        mockMvc.perform(post("/supply-requests/{id}/submit", requestId).session(chef).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(statusOf(requestId)).isEqualTo("SOUMISE");

        // PC : prise en charge puis expedition de 20 unites (stock PC 250 -> 230)
        MockHttpSession pharmacie = login("resp_pharmacie", DEMO_PASSWORD);
        mockMvc.perform(post("/supply-requests/{id}/take-charge", requestId).session(pharmacie).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(statusOf(requestId)).isEqualTo("EN_COURS_TRAITEMENT");

        Long itemId = jdbcTemplate.queryForObject(
                "SELECT id FROM supply_request_items WHERE supply_request_id = ?", Long.class, requestId);
        mockMvc.perform(post("/supply-requests/{id}/ship", requestId).session(pharmacie).with(csrf())
                        .param("approvals[0].itemId", itemId.toString())
                        .param("approvals[0].approvedQuantity", "20"))
                .andExpect(status().is3xxRedirection());
        assertThat(statusOf(requestId)).isEqualTo("EXPEDIEE");
        assertThat(stockOf(PC_ID, MED001_ID)).isEqualTo(230);

        // Valorisation de la sortie : 20 x 500 = 10000
        BigDecimal valuation = jdbcTemplate.queryForObject(
                "SELECT amount FROM financial_transactions WHERE reference_supply_request_id = ?",
                BigDecimal.class, requestId);
        assertThat(valuation).isEqualByComparingTo("10000.00");

        // CM01 : confirmation de livraison (stock CM01 0 -> 20)
        mockMvc.perform(post("/supply-requests/{id}/confirm-delivery", requestId).session(chef).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(statusOf(requestId)).isEqualTo("LIVREE");
        assertThat(stockOf(CM01_ID, MED001_ID)).isEqualTo(20);

        Integer delivered = jdbcTemplate.queryForObject(
                "SELECT quantity_delivered FROM supply_request_items WHERE supply_request_id = ?",
                Integer.class, requestId);
        assertThat(delivered).isEqualTo(20);
    }

    @Test
    void circuit_dc_vers_daas_traite_par_commande_fournisseur() throws Exception {
        // DC demande 40 x MED001 a la DAAS
        MockHttpSession depot = login("resp_depot", DEMO_PASSWORD);
        Long requestId = createRequest(depot, daasEntityId(), 40);
        mockMvc.perform(post("/supply-requests/{id}/submit", requestId).session(depot).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(statusOf(requestId)).isEqualTo("SOUMISE");

        // La DAAS traite la demande en creant une commande fournisseur liee
        MockHttpSession daas = login("agent_daas", DEMO_PASSWORD);
        mockMvc.perform(post("/orders").session(daas).with(csrf())
                        .param("supplierEntityId", FRN01_ID.toString())
                        .param("supplyRequestId", requestId.toString())
                        .param("lines[0].productId", MED001_ID.toString())
                        .param("lines[0].quantityRequested", "40"))
                .andExpect(status().is3xxRedirection());
        Long orderId = jdbcTemplate.queryForObject("SELECT max(id) FROM orders", Long.class);
        assertThat(orderId).isNotNull();

        // La demande passe en cours de traitement des l'emission de la commande
        assertThat(statusOf(requestId)).isEqualTo("EN_COURS_TRAITEMENT");

        // Circuit de validation complet
        mockMvc.perform(post("/orders/{id}/submit", orderId).session(daas).with(csrf()))
                .andExpect(status().is3xxRedirection());
        MockHttpSession dbi = login("dbi_valideur", DEMO_PASSWORD);
        mockMvc.perform(post("/orders/{id}/validate-dbi", orderId).session(dbi).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/orders/{id}/send", orderId).session(daas).with(csrf()))
                .andExpect(status().is3xxRedirection());

        // Reception complete au DC : la demande passe a LIVREE
        Long orderItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM order_items WHERE order_id = ?", Long.class, orderId);
        mockMvc.perform(post("/orders/{id}/receive", orderId).session(depot).with(csrf())
                        .param("receptions[0].itemId", orderItemId.toString())
                        .param("receptions[0].receivedQuantity", "40"))
                .andExpect(status().is3xxRedirection());
        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orders", Integer.class);
        assertThat(orderCount).isEqualTo(1);
        assertThat(orderStatusOf(orderId)).isEqualTo("RECUE");
        assertThat(statusOf(requestId)).isEqualTo("LIVREE");
        assertThat(stockOf(DC_ID, MED001_ID)).isEqualTo(540);
    }

    @Test
    void relation_cm_vers_dc_interdite() throws Exception {
        // Le CM ne peut solliciter que la Pharmacie Centrale.
        MockHttpSession chef = login("chef_cm01", DEMO_PASSWORD);
        mockMvc.perform(post("/supply-requests").session(chef).with(csrf())
                        .param("supplyingEntityId", DC_ID.toString())
                        .param("lines[0].productId", MED001_ID.toString())
                        .param("lines[0].quantityRequested", "10"))
                .andExpect(status().isOk());
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM supply_requests", Integer.class);
        assertThat(count).isZero();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Long daasEntityId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM entities WHERE code = 'DAAS'", Long.class);
    }

    private Long createRequest(MockHttpSession session, Long supplyingId, int quantity) throws Exception {
        mockMvc.perform(post("/supply-requests").session(session).with(csrf())
                        .param("supplyingEntityId", supplyingId.toString())
                        .param("lines[0].productId", MED001_ID.toString())
                        .param("lines[0].quantityRequested", String.valueOf(quantity)))
                .andExpect(status().is3xxRedirection());
        Long requestId = jdbcTemplate.queryForObject("SELECT max(id) FROM supply_requests", Long.class);
        assertThat(requestId).isNotNull();
        return requestId;
    }

    private String statusOf(Long requestId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM supply_requests WHERE id = ?", String.class, requestId);
    }

    private String orderStatusOf(Long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private Integer stockOf(Long entityId, Long productId) {
        return jdbcTemplate.queryForObject(
                "SELECT quantity FROM stocks WHERE entity_id = ? AND product_id = ?",
                Integer.class, entityId, productId);
    }
}
