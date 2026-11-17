package com.pharmacy.stock.it;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration du module de gestion des stocks
 * (creation de mouvements, ajustement d'inventaire, seuils d'alerte).
 */
class StockMovementIT extends AbstractIntegrationTest {

    private static final Long PC_ID = 1L;
    private static final Long CM01_ID = 4L;
    private static final Long MED001_ID = 1L;

    @Test
    void entree_sur_une_structure_sans_stock_cree_la_ligne_a_100() throws Exception {
        var session = loginAdmin();

        mockMvc.perform(post("/stocks/movements").session(session).with(csrf())
                        .param("entityId", CM01_ID.toString())
                        .param("productId", MED001_ID.toString())
                        .param("type", "ENTREE")
                        .param("quantity", "100"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/stocks/movements"));

        Integer quantity = jdbcTemplate.queryForObject(
                "SELECT quantity FROM stocks WHERE entity_id = ? AND product_id = ?",
                Integer.class, CM01_ID, MED001_ID);
        Integer threshold = jdbcTemplate.queryForObject(
                "SELECT min_threshold FROM stocks WHERE entity_id = ? AND product_id = ?",
                Integer.class, CM01_ID, MED001_ID);
        assertThat(quantity).isEqualTo(100);
        assertThat(threshold).isEqualTo(10);

        Integer movements = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM stock_movements WHERE entity_id = ? AND type = 'ENTREE' AND stock_after = 100",
                Integer.class, CM01_ID);
        assertThat(movements).isEqualTo(1);
    }

    @Test
    void sortie_superieure_au_stock_est_rejetee_sans_effet() throws Exception {
        var session = loginAdmin();
        // Stock de demonstration PC / MED001 ramene a 20 unites
        jdbcTemplate.update(
                "UPDATE stocks SET quantity = 20 WHERE entity_id = ? AND product_id = ?",
                PC_ID, MED001_ID);

        mockMvc.perform(post("/stocks/movements").session(session).with(csrf())
                        .param("entityId", PC_ID.toString())
                        .param("productId", MED001_ID.toString())
                        .param("type", "SORTIE")
                        .param("quantity", "50"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("error"));

        Integer quantity = jdbcTemplate.queryForObject(
                "SELECT quantity FROM stocks WHERE entity_id = ? AND product_id = ?",
                Integer.class, PC_ID, MED001_ID);
        assertThat(quantity).isEqualTo(20);
    }

    @Test
    void ajustement_inventaire_fixe_le_stock_physique() throws Exception {
        var session = loginAdmin();
        // Stock de demonstration PC / MED001 ramene a 60 unites
        jdbcTemplate.update(
                "UPDATE stocks SET quantity = 60 WHERE entity_id = ? AND product_id = ?",
                PC_ID, MED001_ID);

        mockMvc.perform(post("/stocks/movements").session(session).with(csrf())
                        .param("entityId", PC_ID.toString())
                        .param("productId", MED001_ID.toString())
                        .param("type", "AJUSTEMENT_INVENTAIRE")
                        .param("quantity", "45"))
                .andExpect(status().is3xxRedirection());

        Integer quantity = jdbcTemplate.queryForObject(
                "SELECT quantity FROM stocks WHERE entity_id = ? AND product_id = ?",
                Integer.class, PC_ID, MED001_ID);
        Integer ecart = jdbcTemplate.queryForObject(
                "SELECT quantity FROM stock_movements WHERE entity_id = ? AND type = 'AJUSTEMENT_INVENTAIRE'",
                Integer.class, PC_ID);
        assertThat(quantity).isEqualTo(45);
        assertThat(ecart).isEqualTo(-15);
    }

    @Test
    void mise_a_jour_du_seuil_declenche_lalerte_de_stock_bas() throws Exception {
        var session = loginAdmin();
        // Stock de demonstration PC / MED001 : 250 unites, seuil 50 (V5)
        jdbcTemplate.update(
                "UPDATE stocks SET quantity = 100 WHERE entity_id = ? AND product_id = ?",
                PC_ID, MED001_ID);
        Long stockId = jdbcTemplate.queryForObject(
                "SELECT id FROM stocks WHERE entity_id = ? AND product_id = ?",
                Long.class, PC_ID, MED001_ID);

        mockMvc.perform(post("/stocks/{id}/threshold", stockId).session(session).with(csrf())
                        .param("minThreshold", "150"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/stocks"));

        Integer threshold = jdbcTemplate.queryForObject(
                "SELECT min_threshold FROM stocks WHERE id = ?", Integer.class, stockId);
        assertThat(threshold).isEqualTo(150);

        mockMvc.perform(get("/stocks").session(session).param("lowStockOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("lowStockCount"));
    }

    @Test
    void journal_des_mouvements_est_filtre_par_type() throws Exception {
        var session = loginAdmin();
        mockMvc.perform(get("/stocks/movements").session(session).param("type", "ENTREE"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("movements"))
                .andExpect(model().attributeExists("movementTypes"));
    }
}
