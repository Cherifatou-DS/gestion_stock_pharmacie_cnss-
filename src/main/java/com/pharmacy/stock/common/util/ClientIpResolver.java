package com.pharmacy.stock.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Resolution de l'adresse IP du client pour la piste d'audit.
 *
 * L'en-tete X-Forwarded-For est uniquement pris en compte lorsque la requete
 * provient d'un proxy de confiance (voir propriete security.audit.trusted-proxies).
 * Cela empeche un client direct de falsifier son adresse IP dans le journal d'audit.
 *
 * En production, configurer l'adresse du reverse-proxy :
 *   security.audit.trusted-proxies=${AUDIT_TRUSTED_PROXIES:127.0.0.1,::1}
 */
@Component
public class ClientIpResolver {

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(
            @Value("${security.audit.trusted-proxies:127.0.0.1,0:0:0:0:0:0:0:1,::1}") String trustedProxiesConfig) {
        this.trustedProxies = Arrays.stream(trustedProxiesConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(IpAddressMatcher::new)
                .toList();
    }

    /**
     * Adresse IP du client reellement a l'origine de la requete.
     */
    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (isTrustedProxy(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        return trustedProxies.stream().anyMatch(matcher -> matcher.matches(remoteAddr));
    }
}
