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
        Tournoi t = tournoiRepository.findTournoiById(tournoiId)
                .orElseThrow(() -> new RegleMetierException("Tournoi introuvable"));
        Joueur capitaine = acces.capitaine(capitaineId);
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
        valider(organisateurId, tournoiDeInscription(inscriptionId), inscriptionId);
    }

    @Transactional
    public void valider(Long organisateurId, Long tournoiId, Long inscriptionId) {
        Inscription i = avantTirage(organisateurId, tournoiId, inscriptionId);
        if (i.getStatut() != StatutInscription.REFUSEE) verifierEnAttente(i);
        Long equipeId = i.getEquipe().getId();
        if (joueurRepository.countByEquipeId(equipeId) < Regles.EFFECTIF_MINIMUM) {
            throw new RegleMetierException("Validation impossible : l'équipe compte moins de "
                    + Regles.EFFECTIF_MINIMUM + " joueurs (refusez la demande)");
        }
        if (joueurRepository.countByEquipeIdAndEstCapitaineTrue(equipeId) != 1) {
            throw new RegleMetierException("Validation impossible : l'équipe doit avoir exactement un capitaine");
        }
        i.setStatut(StatutInscription.VALIDEE);
        i.setMotifRefus(null);
    }

    @Transactional
    public void refuser(Long organisateurId, Long inscriptionId, String motif) {
        refuser(organisateurId, tournoiDeInscription(inscriptionId), inscriptionId, motif);
    }

    @Transactional
    public void refuser(Long organisateurId, Long tournoiId, Long inscriptionId, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new RegleMetierException("Le motif du refus est obligatoire");
        }
        Inscription i = avantTirage(organisateurId, tournoiId, inscriptionId);
        verifierEnAttente(i);
        i.setStatut(StatutInscription.REFUSEE);
        i.setMotifRefus(motif.trim());
    }

    @Transactional
    public void retirer(Long organisateurId, Long tournoiId, Long inscriptionId, String motif) {
        Inscription i = avantTirage(organisateurId, tournoiId, inscriptionId);
        if (i.getStatut() != StatutInscription.VALIDEE) {
            throw new RegleMetierException("Seule une équipe validée peut être retirée");
        }
        if (motif == null || motif.isBlank() || motif.strip().length() > 255) {
            throw new RegleMetierException("Indiquez un motif de retrait de 1 à 255 caractères");
        }
        i.setStatut(StatutInscription.FORFAIT);
        i.setMotifRetrait(motif.strip());
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
                    i.getDateInscription(), i.getStatut(), i.getMotifRefus(), i.getMotifRetrait());
        }).toList();
    }

    /** Demandes de l'équipe du capitaine, avec leur état et le motif d'un éventuel refus. */
    @Transactional(readOnly = true)
    public List<MonInscription> mesInscriptions(Long capitaineId) {
        Joueur capitaine = acces.capitaine(capitaineId);
        return inscriptionRepository.findByEquipeId(capitaine.getEquipe().getId()).stream()
                .map(i -> new MonInscription(i.getId(), i.getTournoi().getId(), i.getTournoi().getNom(),
                        i.getStatut(), i.getMotifRefus(), i.getMotifRetrait()))
                .toList();
    }

    private Inscription avantTirage(Long organisateurId, Long tournoiId, Long inscriptionId) {
        Tournoi t = acces.modifierTournoi(organisateurId, tournoiId);
        if (t.getStatut() != StatutTournoi.INSCRIPTION_OUVERTE && t.getStatut() != StatutTournoi.INSCRIPTION_CLOSE) {
            throw new RegleMetierException("Le tirage a eu lieu. Déclarez le forfait sur la feuille du match concerné.");
        }
        Inscription i = inscriptionRepository.findById(inscriptionId)
                .orElseThrow(() -> new RegleMetierException("Inscription introuvable"));
        if (!i.getTournoi().getId().equals(tournoiId)) throw new AccesRefuseException("Cette inscription appartient à un autre tournoi");
        return i;
    }

    private Long tournoiDeInscription(Long inscriptionId) {
        return tournoiRepository.findByInscriptionsId(inscriptionId)
                .orElseThrow(() -> new RegleMetierException("Inscription introuvable")).getId();
    }

    private void verifierEnAttente(Inscription i) {
        if (i.getTournoi().getStatut() != StatutTournoi.INSCRIPTION_OUVERTE) {
            throw new RegleMetierException("Le tournoi n'est plus ouvert aux inscriptions");
        }
        if (i.getStatut() != StatutInscription.EN_ATTENTE) {
            throw new RegleMetierException("Cette inscription a déjà été traitée");
        }
    }
}
