package com.example.demo.service;

import com.example.demo.dao.entities.Joueur;
import com.example.demo.dao.entities.Organisateur;
import com.example.demo.dao.entities.Utilisateur;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Utilisateur connecté, tel que Spring Security le conserve en session.
 * Les contrôleurs en lisent l'id et le passent aux services.
 *
 * Rôles : organisateur → ROLE_ORGANISATEUR ; joueur → ROLE_JOUEUR ;
 * capitaine → ROLE_JOUEUR et ROLE_CAPITAINE (il hérite des droits du joueur).
 * Les rôles sont calculés à la connexion ; les services vérifient toujours l'état réel en base.
 */
public final class CompteUserDetails implements UserDetails {

    private static final long serialVersionUID = 1L;

    public static final String ROLE_ORGANISATEUR = "ROLE_ORGANISATEUR";
    public static final String ROLE_JOUEUR = "ROLE_JOUEUR";
    public static final String ROLE_CAPITAINE = "ROLE_CAPITAINE";

    private final Long id;
    private final String email;
    private final String motDePasse;
    private final String nomComplet;
    private final boolean motDePasseTemporaire;
    private final List<SimpleGrantedAuthority> roles;

    private CompteUserDetails(Long id, String email, String motDePasse, String nomComplet,
                              boolean motDePasseTemporaire, List<SimpleGrantedAuthority> roles) {
        this.id = id;
        this.email = email;
        this.motDePasse = motDePasse;
        this.nomComplet = nomComplet;
        this.motDePasseTemporaire = motDePasseTemporaire;
        this.roles = roles;
    }

    public static CompteUserDetails de(Utilisateur u) {
        List<SimpleGrantedAuthority> roles = new ArrayList<>();
        if (u instanceof Organisateur) {
            roles.add(new SimpleGrantedAuthority(ROLE_ORGANISATEUR));
        } else if (u instanceof Joueur j) {
            roles.add(new SimpleGrantedAuthority(ROLE_JOUEUR));
            if (j.isEstCapitaine()) roles.add(new SimpleGrantedAuthority(ROLE_CAPITAINE));
        } else {
            throw new IllegalStateException("Type d'utilisateur inconnu : " + u.getClass().getName());
        }
        return new CompteUserDetails(u.getId(), u.getEmail(), u.getMotDePasse(),
                u.getPrenom() + " " + u.getNom(), u.isMotDePasseTemporaire(), List.copyOf(roles));
    }

    public Long getId() { return id; }
    public String getNomComplet() { return nomComplet; }
    public boolean isMotDePasseTemporaire() { return motDePasseTemporaire; }
    public boolean isOrganisateur() { return hasRole(ROLE_ORGANISATEUR); }
    public boolean isCapitaine() { return hasRole(ROLE_CAPITAINE); }

    private boolean hasRole(String role) {
        return roles.stream().anyMatch(r -> r.getAuthority().equals(role));
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return roles; }
    @Override public String getPassword() { return motDePasse; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}