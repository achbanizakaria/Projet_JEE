package com.example.demo.service;

import com.example.demo.dao.entities.*;
import com.example.demo.dao.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ControleAcces {

    private final JoueurRepository joueurRepository;
    private final OrganisateurRepository organisateurRepository;
    private final TournoiRepository tournoiRepository;

    public Joueur joueur(Long id) {
        return joueurRepository.findById(id)
                .orElseThrow(() -> new RegleMetierException("Joueur introuvable"));
    }

    public Joueur capitaine(Long joueurId) {
        Joueur j = joueur(joueurId);
        if (!j.isEstCapitaine() || j.getEquipe() == null) {
            throw new AccesRefuseException("Action réservée au capitaine d'une équipe");
        }
        return j;
    }

    public Organisateur organisateur(Long id) {
        return organisateurRepository.findById(id)
                .orElseThrow(() -> new AccesRefuseException("Action réservée à un organisateur"));
    }

    /** Tournoi, à condition que l'organisateur en soit le responsable. */
    public Tournoi tournoiDe(Long organisateurId, Long tournoiId) {
        Tournoi t = tournoiRepository.findById(tournoiId)
                .orElseThrow(() -> new RegleMetierException("Tournoi introuvable"));
        return verifierResponsable(organisateurId, t);
    }

    public Tournoi modifierTournoi(Long organisateurId, Long tournoiId) {
        return verifierResponsable(organisateurId, tournoiRepository.findTournoiById(tournoiId)
                .orElseThrow(() -> new RegleMetierException("Tournoi introuvable")));
    }

    /** Le stade est commun à tous les tournois. Les planifications concurrentes prennent les verrous dans le même ordre. */
    public Tournoi planifierTournoi(Long organisateurId, Long tournoiId) {
        Tournoi tournoi = tournoiRepository.findAllByOrderByIdAsc().stream().filter(t -> t.getId().equals(tournoiId))
                .findFirst().orElseThrow(() -> new RegleMetierException("Tournoi introuvable"));
        return verifierResponsable(organisateurId, tournoi);
    }

    private Tournoi verifierResponsable(Long organisateurId, Tournoi t) {
        if (!t.getOrganisateur().getId().equals(organisateurId)) {
            throw new AccesRefuseException("Ce tournoi n'est pas placé sous votre responsabilité");
        }
        return t;
    }
}
