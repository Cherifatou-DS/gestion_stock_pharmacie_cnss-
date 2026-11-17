package com.pharmacy.stock.it;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Tests d'intégration de la gestion des structures (création, modification, désactivation).
 */
class EntityCreationIT extends AbstractIntegrationTest {

    private static final java.util.List<String> CODES_TEST = java.util.List.of("CM03", "CM04", "X99");

    @org.junit.jupiter.api.BeforeEach
    void removeEntitiesFromPreviousRuns() {
        jdbcTemplate.update("DELETE FROM entities WHERE id IN (100, 101, 102, 103)"
                + " OR code IN ('CM03', 'CM03MOD', 'CM04', 'X99', 'FRN03', 'FRN04')");
    }

    @Test
    void ladministrateur_cree_une_nouvelle_structure() throws Exception {
        var session = loginAdmin();

        mockMvc.perform(get("/entities/new").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/entities/form"))
                .andExpect(model().attributeExists("entityForm", "structureTypes"));

        mockMvc.perform(post("/entities").session(session).with(csrf())
                        .param("code", "CM03")
                        .param("name", "Centre Médical 3")
                        .param("type", "CENTRE_MEDICAL"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entities"))
                .andExpect(flash().attributeExists("message"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM entities WHERE code = 'CM03' AND type = 'CENTRE_MEDICAL'",
                Integer.class);
        assertThat(count).isEqualTo(1);

        Integer logs = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM action_logs WHERE entity_affected = 'ENTITIES' AND action_type = 'CREATION'",
                Integer.class);
        assertThat(logs).isEqualTo(1);
    }

    @Test
    void le_code_est_converti_en_majuscules_et_le_code_doublon_est_rejete() throws Exception {
        var session = loginAdmin();

        mockMvc.perform(post("/entities").session(session).with(csrf())
                        .param("code", "cm04")
                        .param("name", "Centre Médical 4")
                        .param("type", "CENTRE_MEDICAL"))
                .andExpect(status().is3xxRedirection());

        // Deuxième tentative avec le même code (minuscules) -> erreur métier affichée
        mockMvc.perform(post("/entities").session(session).with(csrf())
                        .param("code", "cm04")
                        .param("name", "Centre Médial 4 bis")
                        .param("type", "CENTRE_MEDICAL"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/entities/form"))
                .andExpect(model().attributeExists("error"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM entities WHERE code = 'CM04'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void le_type_invalide_est_rejete_par_le_service() throws Exception {
        var session = loginAdmin();

        mockMvc.perform(post("/entities").session(session).with(csrf())
                        .param("code", "X99")
                        .param("name", "Structure inconnue")
                        .param("type", "TYPE_FANTOME"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/entities/form"))
                .andExpect(model().attributeExists("error"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM entities WHERE code = 'X99'", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void le_champ_code_est_obligatoire() throws Exception {
        var session = loginAdmin();

        mockMvc.perform(post("/entities").session(session).with(csrf())
                        .param("code", "")
                        .param("name", "Sans code")
                        .param("type", "CENTRE_MEDICAL"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/entities/form"))
                .andExpect(model().attributeHasFieldErrors("entityForm", "code"));
    }

    @Test
    void un_chef_de_service_naccede_pas_a_la_creation_de_structure() throws Exception {
        var session = login("chef_cm01", DEMO_PASSWORD);

        mockMvc.perform(get("/entities/new").session(session))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Modification
    // ------------------------------------------------------------------

    @Test
    void administrateur_modifie_une_structure() throws Exception {
        // Préalable : créer une structure via V5, mais le schéma `it` est vide, on utilise donc un UPDATE direct
        jdbcTemplate.update("INSERT INTO entities (id, code, name, type, is_active) VALUES (100, 'CM03', 'Centre Médical 3', 'CENTRE_MEDICAL', TRUE)");

        var session = loginAdmin();

        mockMvc.perform(get("/entities/{id}/edit", 100).session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/entities/form"))
                .andExpect(model().attributeExists("entityForm", "structureTypes"))
                .andExpect(model().attributeExists("entityId"))
                .andExpect(model().attribute("readonly", false));

        mockMvc.perform(post("/entities/{id}/edit", 100).session(session).with(csrf())
                        .param("code", "CM03MOD")
                        .param("name", "Centre Médical 3 Modifié")
                        .param("type", "CENTRE_MEDICAL"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entities"))
                .andExpect(flash().attributeExists("message"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM entities WHERE code = 'CM03MOD' AND id = 100", Integer.class);
        assertThat(count).isEqualTo(1);

        Integer logs = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM action_logs WHERE entity_affected = 'ENTITIES' AND action_type = 'MODIFICATION'",
                Integer.class);
        assertThat(logs).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Désactivation (cas possible : MACGAZINIER -> mais on a PROTECTED_TYPES)
    // ------------------------------------------------------------------

    @Test
    void administrateur_peux_desactiver_une_structure_dite_non_protegee() throws Exception {
        // Pour tester la désactivation, on crée une structure de type FOURNISSEUR (non protégé)
        jdbcTemplate.update("INSERT INTO entities (id, code, name, type, is_active) VALUES (101, 'FRN03', 'Nouveau Fournisseur', 'FOURNISSEUR', TRUE)");

        var session = loginAdmin();

        mockMvc.perform(post("/entities/{id}/deactivate", 101).session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entities"))
                .andExpect(flash().attributeExists("message"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM entities WHERE id = 101 AND is_active = FALSE", Integer.class);
        assertThat(count).isEqualTo(1);

        Integer logs = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM action_logs WHERE entity_affected = 'ENTITIES' AND action_type = 'DEACTIVATION'",
                Integer.class);
        assertThat(logs).isEqualTo(1);
    }

    @Test
    void administrateur_ne_peut_pas_desactiver_une_structure_proctectee() throws Exception {
        // CM01 et DC sont protégés par défaut (données de référence), création forcée
        jdbcTemplate.update("INSERT INTO entities (id, code, name, type, is_active) VALUES (102, 'CM04', 'CM Protégé', 'CENTRE_MEDICAL', TRUE)");

        var session = loginAdmin();

        mockMvc.perform(post("/entities/{id}/deactivate", 102).session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entities"))
                .andExpect(flash().attributeExists("error"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM entities WHERE id = 102 AND is_active = TRUE", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Réactivation
    // ------------------------------------------------------------------

    @Test
    void administrateur_peut_reactiver_une_structure_desactivee() throws Exception {
        // On désactive une structure FOURNISSEUR (non protégée)
        jdbcTemplate.update("INSERT INTO entities (id, code, name, type, is_active) VALUES (103, 'FRN04', 'Fournisseur Désactivé', 'FOURNISSEUR', FALSE)");

        var session = loginAdmin();

        mockMvc.perform(post("/entities/{id}/reactivate", 103).session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entities"))
                .andExpect(flash().attributeExists("message"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM entities WHERE id = 103 AND is_active = TRUE", Integer.class);
        assertThat(count).isEqualTo(1);

        Integer logs = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM action_logs WHERE entity_affected = 'ENTITIES' AND action_type = 'REACTIVATION'",
                Integer.class);
        assertThat(logs).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Contrôle d'accès
    // ------------------------------------------------------------------

    @Test
    void un_chef_de_service_ne_peut_pas_acceder_aux_structures() throws Exception {
        var session = login("chef_cm01", DEMO_PASSWORD);

        mockMvc.perform(get("/entities").session(session))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/entities/new").session(session))
                .andExpect(status().isForbidden());
    }
}