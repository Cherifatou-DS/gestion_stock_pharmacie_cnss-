package com.pharmacy.stock.config;

import com.pharmacy.stock.common.util.ClientIpResolver;
import com.pharmacy.stock.modules.audit.service.AuditService;
import com.pharmacy.stock.modules.user.security.CustomUserDetails;
import com.pharmacy.stock.modules.user.security.ForcePasswordChangeFilter;
import com.pharmacy.stock.modules.user.security.LoginAttemptService;
import com.pharmacy.stock.modules.user.security.LoginLockoutFilter;
import com.pharmacy.stock.modules.user.service.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String LOGIN_URL = "/login";
    private static final String DASHBOARD_URL = "/dashboard";
    private static final String CHANGE_PASSWORD_URL = "/change-password";

    /**
     * CSP pragmatique : les gabarits AdminLTE embarquent des styles et scripts
     * en ligne ('unsafe-inline') et la charte graphique charge Font Awesome
     * depuis cdnjs. A resserrer si les assets deviennent auto-heberges.
     */
    private static final String CONTENT_SECURITY_POLICY = """
            default-src 'self'; \
            img-src 'self' data:; \
            style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com; \
            font-src 'self' https://cdnjs.cloudflare.com; \
            script-src 'self' 'unsafe-inline'; \
            frame-ancestors 'self'; \
            base-uri 'self'; \
            form-action 'self'""";

    private final CustomUserDetailsService userDetailsService;
    private final AuditService auditService;
    private final LoginAttemptService loginAttemptService;
    private final ClientIpResolver clientIpResolver;

    /**
     * Chaine de filtres : pages publiques (login, ressources statiques),
     * accès restreint par rôle, gestion des sessions, protection brute-force
     * et changement de mot de passe forcé.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Ressources réellement utilisées par l'application ; les pages de
                // démonstration AdminLTE (/dist/*.html, /dist/docs, ...) ne sont plus publiques.
                .requestMatchers("/css/**", "/js/**", "/img/**", "/favicon.ico",
                        "/dist/css/**", "/dist/js/**", "/dist/assets/**",
                        "/plugins/**", "/webjars/**").permitAll()
                .requestMatchers(LOGIN_URL).permitAll()
                .requestMatchers("/users/**", "/entities/**", "/roles/**").hasRole("ADMIN")
                .requestMatchers("/products/**").hasAnyRole("ADMIN", "RESPONSABLE_PHARMACIE")
                .requestMatchers("/stocks/**").hasAnyRole("ADMIN", "RESPONSABLE_PHARMACIE",
                        "RESPONSABLE_DEPOT", "MAGASINIER", "CHEF_SERVICE", "AGENT_APPROVISIONNEMENT", "DAAS")
                .requestMatchers("/supply-requests/**").hasAnyRole("ADMIN", "RESPONSABLE_PHARMACIE",
                        "RESPONSABLE_DEPOT", "MAGASINIER", "CHEF_SERVICE", "AGENT_APPROVISIONNEMENT",
                        "DAAS", "DBI")
                .requestMatchers("/orders/**").hasAnyRole("ADMIN", "RESPONSABLE_DEPOT", "MAGASINIER",
                        "DAAS", "DBI")
                .requestMatchers("/finance/**").hasAnyRole("ADMIN", "DBI")
                .requestMatchers("/audit/**").hasAnyRole("ADMIN", "DBI")
                .requestMatchers(CHANGE_PASSWORD_URL).authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage(LOGIN_URL)
                .successHandler(authenticationSuccessHandler())
                .failureHandler(authenticationFailureHandler())
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler(logoutSuccessHandler())
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .sessionManagement(session -> session
                .sessionFixation().migrateSession()
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
                .expiredUrl("/login?expired")
            )
            .exceptionHandling(ex -> ex
                .accessDeniedPage("/403")
            )
            .headers(headers -> headers
                // HSTS effectif sur les requetes HTTPS (profil prod).
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31_536_000))
                .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
            )
            // Protection brute-force puis changement de mot de passe oblige.
            .addFilterBefore(new LoginLockoutFilter(loginAttemptService, clientIpResolver),
                    UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new ForcePasswordChangeFilter(),
                    UsernamePasswordAuthenticationFilter.class)
            .userDetailsService(userDetailsService);

        return http.build();
    }

    /**
     * Connexion reussie : reinitialisation du compteur brute-force,
     * audit puis redirection (changement de mot de passe oblige le cas echeant).
     */
    private AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            if (authentication.getPrincipal() instanceof CustomUserDetails principal) {
                loginAttemptService.recordSuccess(
                        LoginAttemptService.key(principal.getUsername(), clientIpResolver.resolve(request)));
                logAuthentication(request, authentication, "LOGIN_SUCCESS", "Connexion réussie");
                response.sendRedirect(request.getContextPath()
                        + (principal.isMustChangePassword() ? CHANGE_PASSWORD_URL : DASHBOARD_URL));
            }
        };
    }

    /**
     * Echec d'authentification : comptabilisation pour le verrouillage
     * brute-force puis retour sur le formulaire avec un message generique
     * (pas d'enumeration de comptes).
     */
    private AuthenticationFailureHandler authenticationFailureHandler() {
        return (request, response, exception) -> {
            String username = request.getParameter("username");
            if (username != null && !username.isBlank()) {
                String key = LoginAttemptService.key(username, clientIpResolver.resolve(request));
                loginAttemptService.recordFailure(key);
                log.warn("Échec d'authentification pour '{}' depuis {} (tentative {}/{})",
                        username, clientIpResolver.resolve(request),
                        loginAttemptService.isLocked(key) ? LoginAttemptService.MAX_ATTEMPTS : "-",
                        LoginAttemptService.MAX_ATTEMPTS);
            }
            response.sendRedirect(request.getContextPath() + "/login?error");
        };
    }

    private LogoutSuccessHandler logoutSuccessHandler() {
        return (request, response, authentication) -> {
            if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details) {
                auditService.record(details.getId(), "LOGOUT", "AUTH", details.getId(),
                        "Déconnexion de l'utilisateur " + details.getUsername(),
                        clientIpResolver.resolve(request));
            }
            response.sendRedirect(request.getContextPath() + "/login?logout");
        };
    }

    private void logAuthentication(HttpServletRequest request, Authentication authentication,
                                   String actionType, String details) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails principal) {
            auditService.record(principal.getId(), actionType, "AUTH", principal.getId(),
                    details + " : " + principal.getUsername(), clientIpResolver.resolve(request));
        }
    }

    /**
     * Publie les evenements de destruction de session vers SessionRegistry :
     * indispensable pour que maximumSessions(1) detecte les deconnexions
     * et n'empeche pas une reconnexion legitime.
     */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
