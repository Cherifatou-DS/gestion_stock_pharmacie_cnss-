package com.pharmacy.stock.modules.user.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Empeche un utilisateur dont le mot de passe est connu ou reinitialise
 * d'acceder a l'application avant le changement de mot de passe obligatoire.
 * Toute requete est redirigee vers /change-password (liste blanche ci-dessous).
 */
public class ForcePasswordChangeFilter extends OncePerRequestFilter {

    private static final String CHANGE_PASSWORD_URL = "/change-password";

    /** Routes accessibles pendant le changement de mot de passe force. */
    private static final Set<String> ALLOWED_PATHS = Set.of(
            CHANGE_PASSWORD_URL, "/logout", "/error");

    /** Ressources statiques (styles du formulaire, logo...). */
    private static final List<String> ALLOWED_PREFIXES = List.of(
            "/css/", "/js/", "/img/", "/dist/css/", "/dist/js/", "/dist/assets/",
            "/plugins/", "/webjars/", "/favicon.ico");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails
                && userDetails.isMustChangePassword()) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            boolean allowed = ALLOWED_PATHS.contains(path)
                    || ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
            if (!allowed) {
                response.sendRedirect(request.getContextPath() + CHANGE_PASSWORD_URL);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
