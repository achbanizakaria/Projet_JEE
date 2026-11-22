package com.example.demo.service;

import com.example.demo.dao.entities.*;
import com.example.demo.dao.repositories.*;
import com.example.demo.dto.CompteForm;
import com.example.demo.dto.MotDePasseForm;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CompteService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    /** À utiliser aussi à la connexion : les e-mails sont enregistrés en minuscules. */
    public static String normaliserEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Inscription publique : ne crée jamais qu'un joueur, sans équipe. */
    @Transactional
    public Long inscrireJoueur(CompteForm form) {
        Joueur j = new Joueur();
        remplir(j, form);
        return utilisateurRepository.save(j).getId();
    }

    /** Hors inscription publique (création au démarrage de l'application). */
    @Transactional
    public Long creerOrganisateur(CompteForm form, String fonction) {
        Organisateur o = new Organisateur();
        remplir(o, form);
        o.setFonction(fonction);
        return utilisateurRepository.save(o).getId();
    }

    /** Remplace le mot de passe (obligatoire après un mot de passe temporaire). */
    @Transactional
    public void changerMotDePasse(Long utilisateurId, MotDePasseForm f) {
        Utilisateur u = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RegleMetierException("Utilisateur introuvable"));
        if (!passwordEncoder.matches(f.getAncienMotDePasse(), u.getMotDePasse())) {
            throw new RegleMetierException("Le mot de passe actuel est incorrect");
        }
        if (f.getAncienMotDePasse().equals(f.getNouveauMotDePasse())) {
            throw new RegleMetierException("Le nouveau mot de passe doit être différent de l'ancien");
        }
        u.setMotDePasse(passwordEncoder.encode(f.getNouveauMotDePasse()));
        u.setMotDePasseTemporaire(false);
    }

    private void remplir(Utilisateur u, CompteForm form) {
        String email = normaliserEmail(form.getEmail());
        if (utilisateurRepository.existsByEmail(email)) {
            throw new RegleMetierException("Cette adresse e-mail est déjà utilisée");
        }
        u.setNom(form.getNom().trim());
        u.setPrenom(form.getPrenom().trim());
        u.setEmail(email);
        u.setMotDePasse(passwordEncoder.encode(form.getMotDePasse()));
    }
}