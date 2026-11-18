package com.pharmacy.stock.modules.user.security;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protection contre les attaques par force brute sur le formulaire de connexion.
 *
 * Apres MAX_ATTEMPTS echecs consecutifs pour une cle (identifiant + IP),
 * le compte est verrouille temporairement. Un succes reinitialise le compteur.
 * Le comptage est fait en memoire (mono-instance) ; pour un deploiement
 * multi-instances, adosser ce service a Redis ou a la base de donnees.
 */
@Service
public class LoginAttemptService {

    /** Nombre d'echecs tolere avant verrouillage. */
    public static final int MAX_ATTEMPTS = 5;

    /** Duree de verrouillage apres le dernier echec tolere. */
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private record Entry(int failures, Instant lockedUntil) {
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    /**
     * Construit la cle de suivi a partir de l'identifiant saisi et de l'adresse IP.
     */
    public static String key(String username, String clientIp) {
        String user = username == null ? "" : username.trim().toLowerCase();
        String ip = clientIp == null ? "" : clientIp;
        return user + "|" + ip;
    }

    /**
     * Verrouille temporairement apres le Nieme echec consecutif.
     */
    public void recordFailure(String key) {
        entries.compute(key, (k, current) -> {
            boolean expired = current != null && current.lockedUntil() != null
                    && current.lockedUntil().isBefore(Instant.now());
            int failures = (current == null || expired) ? 1 : current.failures() + 1;
            Instant lockedUntil = failures >= MAX_ATTEMPTS ? Instant.now().plus(LOCK_DURATION) : null;
            return new Entry(failures, lockedUntil);
        });
    }

    /**
     * Connexion reussie : reinitialisation du compteur.
     */
    public void recordSuccess(String key) {
        entries.remove(key);
    }

    /**
     * Le couple identifiant/IP est-il actuellement verrouille ?
     */
    public boolean isLocked(String key) {
        Entry entry = entries.get(key);
        if (entry == null || entry.lockedUntil() == null) {
            return false;
        }
        if (entry.lockedUntil().isBefore(Instant.now())) {
            entries.remove(key);
            return false;
        }
        return true;
    }

    /**
     * Secondes restantes avant deverrouillage, 0 si non verrouille.
     */
    public long secondsRemaining(String key) {
        Entry entry = entries.get(key);
        if (entry == null || entry.lockedUntil() == null) {
            return 0;
        }
        return Math.max(0, Duration.between(Instant.now(), entry.lockedUntil()).toSeconds());
    }
}
