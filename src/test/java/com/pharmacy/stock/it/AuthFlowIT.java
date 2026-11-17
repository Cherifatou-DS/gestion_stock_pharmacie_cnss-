package com.pharmacy.stock.it;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Tests d'integration de l'authentification et du contrôle d'accès par rôle.
 */
class AuthFlowIT extends AbstractIntegrationTest {

    @Test
    void connexion_admin_redirige_vers_le_tableau_de_bord() throws Exception {
        mockMvc.perform(SecurityMockMvcRequestBuilders.formLogin().user("admin").password("Admin@123"))
                .andExpect(authenticated().withUsername("admin"))
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void connexion_avec_mot_de_passe_invalide_est_rejetee() throws Exception {
        mockMvc.perform(SecurityMockMvcRequestBuilders.formLogin().user("admin").password("mauvais"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void page_de_login_accessible_sans_authentification() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/auth/login"));
    }

    @Test
    void acces_sans_authentification_redirige_vers_le_login() throws Exception {
        mockMvc.perform(get("/stocks"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void un_chef_de_service_naccede_pas_a_la_gestion_des_utilisateurs() throws Exception {
        var session = login("chef_cm01", DEMO_PASSWORD);
        mockMvc.perform(get("/users").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void un_chef_de_service_naccede_pas_au_journal_daudit() throws Exception {
        var session = login("chef_cm01", DEMO_PASSWORD);
        mockMvc.perform(get("/audit").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void un_responsable_pharmacie_peut_consulter_les_stocks() throws Exception {
        var session = login("resp_pharmacie", DEMO_PASSWORD);
        mockMvc.perform(get("/stocks").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void deconnexion_redirige_vers_le_login() throws Exception {
        var session = login("admin", "Admin@123");
        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
    }
}
