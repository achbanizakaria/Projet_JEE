package com.example.demo.config;

import com.example.demo.dao.repositories.UtilisateurRepository;
import com.example.demo.dto.CompteForm;
import com.example.demo.service.CompteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Crée au démarrage le compte de l'organisateur (aucune page publique ne permet de le faire).
 * L'e-mail et le mot de passe viennent de application.properties (app.organisateur.*).
 * Sans effet si le compte existe déjà.
 */
@Component
public class InitialisationComptes implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(InitialisationComptes.class);
    static final int LONGUEUR_MINIMALE = 8;

    private final CompteService compteService;
    private final UtilisateurRepository utilisateurRepository;
    private final String email;
    private final String motDePasse;
    private final String nom;
    private final String prenom;
    private final String fonction;

    public InitialisationComptes(CompteService compteService,
                                 UtilisateurRepository utilisateurRepository,
                                 @Value("${app.organisateur.email:}") String email,
                                 @Value("${app.organisateur.password:}") String motDePasse,
                                 @Value("${app.organisateur.nom:Organisateur}") String nom,
                                 @Value("${app.organisateur.prenom:Principal}") String prenom,
                                 @Value("${app.organisateur.fonction:Responsable du service des sports}") String fonction) {
        this.compteService = compteService;
        this.utilisateurRepository = utilisateurRepository;
        this.email = email;
        this.motDePasse = motDePasse;
        this.nom = nom;
        this.prenom = prenom;
        this.fonction = fonction;
    }

    @Override
    public void run(String... args) {
        creerOrganisateurSiAbsent();
    }

    /** @return true si le compte vient d'être créé. */
    public boolean creerOrganisateurSiAbsent() {
        if (email.isBlank() || motDePasse.isBlank()) {
            LOG.warn("Aucun compte organisateur créé : renseignez app.organisateur.email et app.organisateur.password");
            return false;
        }
        if (motDePasse.length() < LONGUEUR_MINIMALE) {
            LOG.warn("Aucun compte organisateur créé : le mot de passe doit compter au moins {} caractères",
                    LONGUEUR_MINIMALE);
            return false;
        }
        String adresse = CompteService.normaliserEmail(email);
        if (utilisateurRepository.existsByEmail(adresse)) {
            return false;
        }
        CompteForm form = new CompteForm();
        form.setNom(nom);
        form.setPrenom(prenom);
        form.setEmail(adresse);
        form.setMotDePasse(motDePasse);
        compteService.creerOrganisateur(form, fonction);
        LOG.info("Compte organisateur créé : {}", adresse);
        return true;
    }
}