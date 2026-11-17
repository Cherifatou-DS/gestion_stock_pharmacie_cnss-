package com.pharmacy.stock.modules.user.security;

import com.pharmacy.stock.modules.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adaptateur Spring Security basé sur l'utilisateur de la base de données.
 * Expose le rôle (autorité unique) et les informations de la structure de
 * rattachement (Centre Médical, Pharmacie Centrale, Dépôt Central, DBI).
 * Les données de structure sont copiées (et non référencées) pour éviter
 * toute LazyInitializationException hors session Hibernate.
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String fullName;
    private final String email;
    private final String role;
    private final Long entityId;
    private final String entityCode;
    private final String entityName;
    private final boolean active;

    public CustomUserDetails(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPasswordHash();
        this.fullName = (user.getFirstName() + " " + user.getLastName()).trim();
        this.email = user.getEmail();
        this.role = user.getRole().getName();
        this.entityId = user.getEntity().getId();
        this.entityCode = user.getEntity().getCode();
        this.entityName = user.getEntity().getName();
        this.active = Boolean.TRUE.equals(user.getIsActive());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
