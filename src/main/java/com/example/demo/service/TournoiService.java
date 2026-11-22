package com.example.demo.service;

import com.example.demo.dao.entities.*;
import com.example.demo.dao.repositories.*;
import com.example.demo.dto.TournoiForm;
import com.example.demo.dto.Vues.TournoiResume;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TournoiService {

    private final TournoiRepository tournoiRepository;
    private final MatchRepository matchRepository;
    private final InscriptionRepository inscriptionRepository;
    private final ControleAcces acces;

    @Transactional
    public TournoiResume creer(Long organisateurId, TournoiForm f) {
        Organisateur organisateur = acces.organisateur(organisateurId);
        valider(f);
        Tournoi t = new Tournoi();
        t.setNom(f.getNom().trim());
        t.setSport(f.getSport().trim());
        t.setDateDebut(f.getDateDebut());
        t.setDateFin(f.getDateFin());
        t.setNbGroupes(f.getNbGroupes());
        t.setNbQualifiesParGroupe(f.getNbQualifiesParGroupe());
        t.setDureeMatchMinutes(f.getDureeMatchMinutes());
        t.setHeureOuverture(f.getHeureOuverture());
        t.setHeureFermeture(f.getHeureFermeture());
        t.setReposMinutes(f.getReposMinutes());
        t.setScoreForfait(f.getScoreForfait());
        t.setStatut(StatutTournoi.INSCRIPTION_OUVERTE);
        t.setOrganisateur(organisateur);
        return resume(tournoiRepository.save(t));
    }

    /** Tournois dont l'organisateur est responsable, le plus récent en premier. */
    @Transactional(readOnly = true)
    public List<TournoiResume> tournoisDe(Long organisateurId) {
        acces.organisateur(organisateurId);
        return tournoiRepository.findByOrganisateurIdOrderByDateDebutDesc(organisateurId)
                .stream().map(TournoiService::resume).toList();
    }

    @Transactional(readOnly = true)
    public TournoiResume tournoi(Long organisateurId, Long tournoiId) {
        return resume(acces.tournoiDe(organisateurId, tournoiId));
    }

    /** Ferme les inscriptions ; toutes les demandes doivent avoir été traitées. */
    @Transactional
    public void cloturerInscriptions(Long organisateurId, Long tournoiId) {
        Tournoi t = acces.tournoiDe(organisateurId, tournoiId);
        if (t.getStatut() != StatutTournoi.INSCRIPTION_OUVERTE) {
            throw new RegleMetierException("Les inscriptions ne sont pas ouvertes");
        }
        if (inscriptionRepository.countByTournoiIdAndStatut(tournoiId, StatutInscription.EN_ATTENTE) > 0) {
            throw new RegleMetierException("Traitez toutes les demandes en attente avant de clôturer les inscriptions");
        }
        t.setStatut(StatutTournoi.INSCRIPTION_CLOSE);
    }

    /** Clôture après la finale : rangs finaux 1 à 4, puis archivage. */
    @Transactional
    public void cloturer(Long organisateurId, Long tournoiId) {
        Tournoi t = acces.tournoiDe(organisateurId, tournoiId);
        if (t.getStatut() != StatutTournoi.PHASE_ELIMINATOIRE) {
            throw new RegleMetierException("Le tournoi n'est pas en phase éliminatoire");
        }
        if (matchRepository.countByTournoiIdAndStatutIn(tournoiId,
                List.of(StatutMatch.PLANIFIE, StatutMatch.EN_COURS)) > 0) {
            throw new RegleMetierException("Clôture impossible : des matchs ne sont pas terminés");
        }
        Match finale = unique(tournoiId, PhaseMatch.FINALE);
        attribuerRangs(t, finale, 1, 2);
        matchRepository.findByTournoiIdAndPhase(tournoiId, PhaseMatch.PETITE_FINALE)
                .stream().findFirst().ifPresent(pf -> attribuerRangs(t, pf, 3, 4));
        t.setStatut(StatutTournoi.TERMINE);
    }

    private static TournoiResume resume(Tournoi t) {
        return new TournoiResume(t.getId(), t.getNom(), t.getSport(), t.getDateDebut(),
                t.getDateFin(), t.getStatut());
    }

    private Match unique(Long tournoiId, PhaseMatch phase) {
        return matchRepository.findByTournoiIdAndPhase(tournoiId, phase).stream().findFirst()
                .orElseThrow(() -> new RegleMetierException("Match introuvable : " + phase));
    }

    private void attribuerRangs(Tournoi t, Match m, int rangVainqueur, int rangPerdant) {
        EquipeMatch v = OutilsMatch.vainqueur(m)
                .orElseThrow(() -> new RegleMetierException("Vainqueur indéterminé pour " + m.getPhase()));
        EquipeMatch p = OutilsMatch.perdant(m)
                .orElseThrow(() -> new RegleMetierException("Perdant indéterminé pour " + m.getPhase()));
        attribuer(t, v.getEquipe(), rangVainqueur);
        attribuer(t, p.getEquipe(), rangPerdant);
    }

    private void attribuer(Tournoi t, Equipe equipe, int rang) {
        Inscription i = inscriptionRepository.findByTournoiIdAndEquipeId(t.getId(), equipe.getId())
                .orElseThrow(() -> new RegleMetierException("Inscription introuvable"));
        i.setRangFinal(rang);
    }

    private void valider(TournoiForm f) {
        if (f.getDateFin().isBefore(f.getDateDebut())) {
            throw new RegleMetierException("La date de fin est antérieure à la date de début");
        }
        int total = f.getNbGroupes() * f.getNbQualifiesParGroupe();
        if (total != 2 && total != 4 && total != 8 && total != 16) {
            throw new RegleMetierException(
                    "Le total de qualifiés (groupes × qualifiés par groupe) doit valoir 2, 4, 8 ou 16");
        }
        if (!f.getHeureFermeture().isAfter(f.getHeureOuverture())) {
            throw new RegleMetierException("L'heure de fermeture doit suivre l'heure d'ouverture");
        }
        long minutesParJour = Duration.between(f.getHeureOuverture(), f.getHeureFermeture()).toMinutes();
        if (f.getDureeMatchMinutes() > minutesParJour) {
            throw new RegleMetierException("La durée d'un match dépasse la plage horaire du stade");
        }
    }
}