package com.pharmacy.stock.it;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.FormLoginRequestBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;

/**
 * Socle des tests d'integration : contexte complet Spring Boot + MockMvc,
 * base PostgreSQL dediee au schema "it" (voir application-test.properties).
 *
 * Comptes de demonstration issus de V5__insert_demo_accounts.sql (mot de passe Demo@1234).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String DEMO_PASSWORD = "Demo@1234";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * Nettoyage des donnees metier avant chaque test
     * (les comptes et le catalogue de demonstration sont conserves).
     * Le socle de stocks de demonstration (V5) est restaure apres purge.
     */
    @BeforeEach
    void cleanBusinessData() {
        jdbcTemplate.update("DELETE FROM financial_transactions");
        jdbcTemplate.update("DELETE FROM stock_movements");
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM supply_request_items");
        jdbcTemplate.update("DELETE FROM supply_requests");
        jdbcTemplate.update("DELETE FROM stocks");
        jdbcTemplate.update("DELETE FROM action_logs");
        insertDemoStocks();
    }

    /**
     * Repli du socle de stocks de demonstration defini par V5__insert_demo_accounts.sql.
     */
    private void insertDemoStocks() {
        jdbcTemplate.update("""
                INSERT INTO stocks (entity_id, product_id, quantity, min_threshold, updated_at)
                SELECT e.id, p.id, 250, 50, CURRENT_TIMESTAMP FROM entities e, products p
                WHERE e.code = 'PC' AND p.code = 'MED001'
                  AND NOT EXISTS (SELECT 1 FROM stocks s WHERE s.entity_id = e.id AND s.product_id = p.id)
                """);
        jdbcTemplate.update("""
                INSERT INTO stocks (entity_id, product_id, quantity, min_threshold, updated_at)
                SELECT e.id, p.id, 80, 20, CURRENT_TIMESTAMP FROM entities e, products p
                WHERE e.code = 'PC' AND p.code = 'MED002'
                  AND NOT EXISTS (SELECT 1 FROM stocks s WHERE s.entity_id = e.id AND s.product_id = p.id)
                """);
        jdbcTemplate.update("""
                INSERT INTO stocks (entity_id, product_id, quantity, min_threshold, updated_at)
                SELECT e.id, p.id, 500, 50, CURRENT_TIMESTAMP FROM entities e, products p
                WHERE e.code = 'DC' AND p.code = 'MED001'
                  AND NOT EXISTS (SELECT 1 FROM stocks s WHERE s.entity_id = e.id AND s.product_id = p.id)
                """);
        jdbcTemplate.update("""
                INSERT INTO stocks (entity_id, product_id, quantity, min_threshold, updated_at)
                SELECT e.id, p.id, 200, 20, CURRENT_TIMESTAMP FROM entities e, products p
                WHERE e.code = 'DC' AND p.code = 'MED002'
                  AND NOT EXISTS (SELECT 1 FROM stocks s WHERE s.entity_id = e.id AND s.product_id = p.id)
                """);
    }

    /**
     * Connexion via le formulaire de securite et conservation de la session.
     */
    protected MockHttpSession login(String username, String password) {
        FormLoginRequestBuilder request = formLogin().user(username).password(password);
        MvcResult result;
        try {
            result = mockMvc.perform(request).andExpect(authenticated()).andReturn();
        } catch (Exception e) {
            throw new IllegalStateException("Échec de connexion avec le compte " + username, e);
        }
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).as("Session après connexion de %s", username).isNotNull();
        return session;
    }

    protected MockHttpSession loginAdmin() {
        return login("admin", "Admin@123");
    }
}
