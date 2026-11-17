package com.pharmacy.stock.config;

import com.pharmacy.stock.modules.audit.service.AuditService;
import com.pharmacy.stock.modules.user.security.CustomUserDetails;
import com.pharmacy.stock.modules.user.service.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String LOGIN_URL = "/login";
    private static final String DASHBOARD_URL = "/dashboard";

    private final CustomUserDetailsService userDetailsService;
    private final AuditService auditService;

    /**
     * Chaine de filtres : pages publiques (login, ressources statiques),
     * accès restreint par rôle et gestion des sessions.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/img/**", "/dist/**", "/plugins/**", "/webjars/**").permitAll()
                .requestMatchers(LOGIN_URL).permitAll()
                .requestMatchers("/users/**", "/entities/**", "/roles/**").hasRole("ADMIN")
                .requestMatchers("/products/**").hasAnyRole("ADMIN", "RESPONSABLE_PHARMACIE")
                .requestMatchers("/stocks/**").hasAnyRole("ADMIN", "RESPONSABLE_PHARMACIE",
                        "RESPONSABLE_DEPOT", "MAGASINIER", "CHEF_SERVICE", "AGENT_APPROVISIONNEMENT")
                .requestMatchers("/orders/**").hasAnyRole("ADMIN", "RESPONSABLE_PHARMACIE",
                        "RESPONSABLE_DEPOT", "MAGASINIER", "CHEF_SERVICE", "AGENT_APPROVISIONNEMENT", "DBI")
                .requestMatchers("/audit/**").hasAnyRole("ADMIN", "DBI")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage(LOGIN_URL)
                .failureUrl("/login?error")
                .successHandler(authenticationSuccessHandler())
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
            .userDetailsService(userDetailsService);

        return http.build();
    }

    private AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            logAuthentication(request, authentication, "LOGIN_SUCCESS", "Connexion réussie");
            response.sendRedirect(request.getContextPath() + DASHBOARD_URL);
        };
    }

    private LogoutSuccessHandler logoutSuccessHandler() {
        return (request, response, authentication) -> {
            if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details) {
                auditService.record(details.getId(), "LOGOUT", "AUTH", details.getId(),
                        "Déconnexion de l'utilisateur " + details.getUsername(),
                        clientIp(request));
            }
            response.sendRedirect(request.getContextPath() + "/login?logout");
        };
    }

    private void logAuthentication(HttpServletRequest request, Authentication authentication,
                                   String actionType, String details) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails principal) {
            auditService.record(principal.getId(), actionType, "AUTH", principal.getId(),
                    details + " : " + principal.getUsername(), clientIp(request));
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
