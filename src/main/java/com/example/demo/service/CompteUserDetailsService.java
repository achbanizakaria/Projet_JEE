package com.example.demo.service;

import com.example.demo.dao.entities.Utilisateur;
import com.example.demo.dao.repositories.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Charge un compte par son e-mail (normalisé comme à l'inscription : espaces retirés, minuscules). */
@Service
@RequiredArgsConstructor
public class CompteUserDetailsService implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException("Identifiants incorrects");
        }
        Utilisateur u = utilisateurRepository.findByEmail(CompteService.normaliserEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("Identifiants incorrects"));
        return CompteUserDetails.de(u);
    }
}