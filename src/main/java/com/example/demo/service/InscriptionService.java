package com.example.demo.service;

import com.example.demo.dao.entities.*;
import com.example.demo.dao.repositories.*;
import com.example.demo.dto.Vues.InscriptionVue;
import com.example.demo.dto.Vues.MonInscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InscriptionService {

    private final InscriptionRepository inscriptionRepository;
    private final TournoiRepository tournoiRepository;
    private final JoueurRepository joueurRepository;
    private final ControleAcces acces;

    /**
     * Demande d'inscription (UC06). Une demande refusée peut être renouvelée :
     * la même inscription repasse « en attente » (une seule ligne par équipe et par tournoi).
     * Retourne l'id de l'inscription.
     */
    @Transactional
    public Long demander(Long capitaineId, Long tournoiId) {
        Joueur capitaine = acces.capitaine(capitaineId);
        Tournoi t = tournoiRepository.findById(tournoiId)
                .orElseThrow(() -> new RegleMetierException("Tournoi introuvable"));
        if (t.getStatut() != StatutTournoi.INSCRIPTION_OUVERTE) {
            throw new RegleMetierException("Les inscriptions ne sont pas ouvertes pour ce tournoi");
        }
        Long equipeId = capitaine.getEquipe().getId();
        Optional<Inscription> existante = inscriptionRepository.findByTournoiIdAndEquipeId(tournoiId, equipeId);
        if (existante.isPresent() && existante.get().getStatut() != StatutInscription.REFUSEE) {
            throw new RegleMetierException("Votre équipe a déjà une inscription pour ce tournoi");
        }
        long effectif = joueurRepository.countByEquipeId(equipeId);
        if (effectif < Regles.EFFECTIF_MINIMUM) {
            throw new RegleMetierException("Effectif insuffisant : il manque "
                    + (Regles.EFFECTIF_MINIMUM - effectif) + " joueur(s)");
        }
        Inscription i = existante.orElseGet(Inscription::new);
        if (existante.isEmpty()) {
            i.setTournoi(t);
            i.setEquipe(capitaine.getEquipe());
        }
        i.setStatut(StatutInscription.EN_ATTENTE);
        i.setMotifRefus(null);
        i.setDateInscription(LocalDate.now());
        return inscriptionRepository.save(i).getId();
    }

    @Transactional
    public void valider(Long organisateurId, Long inscriptionId) {
        Inscription i = enAttente(organisateurId, inscriptionId);
        Long equipeId = i.getEquipe().getId();
        if (joueurRepository.countByEquipeId(equipeId) < Regles.EFFECTIF_MINIMUM) {
            throw new RegleMetierException("Validation impossible : l'équipe compte moins de "
                    + Regles.EFFECTIF_MINIMUM + " joueurs (refusez la demande)");
        }
        if (joueurRepository.countByEquipeIdAndEstCapitaineTrue(equipeId) != 1) {
            throw new RegleMetierException("Validation impossible : l'équipe doit avoir exactement un capitaine");
        }
        i.setStatut(StatutInscription.VALIDEE);
    }

    @Transactional
    public void refuser(Long organisateurId, Long inscriptionId, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new RegleMetierException("Le motif du refus est obligatoire");
        }
        Inscription i = enAttente(organisateurId, inscriptionId);
        i.setStatut(StatutInscription.REFUSEE);
        i.setMotifRefus(motif.trim());
    }

    /** Demandes d'un tournoi, côté organisateur. */
    @Transactional(readOnly = true)
    public List<InscriptionVue> lister(Long organisateurId, Long tournoiId) {
        acces.tournoiDe(organisateurId, tournoiId);
        return inscriptionRepository.findByTournoiId(tournoiId).stream().map(i -> {
            Long equipeId = i.getEquipe().getId();
            return new InscriptionVue(i.getId(), i.getEquipe().getNom(), i.getEquipe().getClub().getNom(),
                    joueurRepository.countByEquipeId(equipeId),
                    joueurRepository.countByEquipeIdAndEstCapitaineTrue(equipeId),
                    i.getDateInscription(), i.getStatut(), i.getMotifRefus());
        }).toList();
    }

    /** Demandes de l'équipe du capitaine, avec leur état et le motif d'un éventuel refus. */
    @Transactional(readOnly = true)
    public List<MonInscription> mesInscriptions(Long capitaineId) {
        Joueur capitaine = acces.capitaine(capitaineId);
        return inscriptionRepository.findByEquipeId(capitaine.getEquipe().getId()).stream()
                .map(i -> new MonInscription(i.getId(), i.getTournoi().getId(), i.getTournoi().getNom(),
                        i.getStatut(), i.getMotifRefus()))
                .toList();
    }

    private Inscription enAttente(Long organisateurId, Long inscriptionId) {
        Inscription i = inscriptionRepository.findById(inscriptionId)
                .orElseThrow(() -> new RegleMetierException("Inscription introuvable"));
        Tournoi t = acces.tournoiDe(organisateurId, i.getTournoi().getId());
        if (t.getStatut() != StatutTournoi.INSCRIPTION_OUVERTE) {
            throw new RegleMetierException("Le tournoi n'est plus ouvert aux inscriptions");
        }
        if (i.getStatut() != StatutInscription.EN_ATTENTE) {
            throw new RegleMetierException("Cette inscription a déjà été traitée");
        }
        return i;
    }
}