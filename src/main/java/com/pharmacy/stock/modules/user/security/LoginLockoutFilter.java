package com.pharmacy.stock.modules.user.security;

import com.pharmacy.stock.common.util.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejette les tentatives de connexion pour un couple identifiant/IP
 * actuellement verrouille (protection brute-force, cf. LoginAttemptService).
 * Positionne avant UsernamePasswordAuthenticationFilter.
 */
public class LoginLockoutFilter extends OncePerRequestFilter {

    private final LoginAttemptService loginAttemptService;
    private final ClientIpResolver clientIpResolver;

    public LoginLockoutFilter(LoginAttemptService loginAttemptService, ClientIpResolver clientIpResolver) {
        this.loginAttemptService = loginAttemptService;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && request.getRequestURI().endsWith("/login")) {
            String key = LoginAttemptService.key(request.getParameter("username"),
                    clientIpResolver.resolve(request));
            if (loginAttemptService.isLocked(key)) {
                response.sendRedirect(request.getContextPath() + "/login?locked");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
