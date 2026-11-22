package com.example.demo.service;

import com.example.demo.dao.entities.*;
import com.example.demo.dao.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PhaseEliminatoireService {

    private record Affiche(Equipe a, Equipe b) {}

    private final ControleAcces acces;
    private final MatchRepository matchRepository;
    private final GroupeRepository groupeRepository;
    private final ClassementService classementService;
    private final PlanificationStade planification;

    @Transactional
    public void generer(Long organisateurId, Long tournoiId) {
        Tournoi t = acces.tournoiDe(organisateurId, tournoiId);
        if (t.getStatut() != StatutTournoi.PHASE_GROUPES) {
            throw new RegleMetierException("La phase éliminatoire se génère pendant la phase de groupes, une seule fois");
        }
        long restants = matchRepository.countByTournoiIdAndPhaseAndStatutNotIn(
                tournoiId, PhaseMatch.GROUPES, List.of(StatutMatch.TERMINE, StatutMatch.FORFAIT));
        if (restants > 0) {
            throw new RegleMetierException(restants + " match(s) de poule restent à jouer");
        }
        int q = t.getNbQualifiesParGroupe();
        List<Groupe> groupes = groupeRepository.findByTournoiIdOrderByNomAsc(tournoiId);
        for (Groupe g : groupes) {
            if (classementService.departageNecessaire(g.getId(), q)) {
                throw new RegleMetierException("Égalité parfaite dans le groupe " + g.getNom()
                        + " pour la dernière place qualificative : lancez le tirage au sort de départage");
            }
        }
        List<List<Equipe>> qualifies = groupes.stream()
                .map(g -> classementService.qualifies(g.getId(), q)).toList();
        int total = groupes.size() * q;

        List<Affiche> premierTour = (q == 2 && groupes.size() % 2 == 0)
                ? affichesParGroupesAppaires(qualifies)
                : affichesParTetesDeSerie(qualifies, total);

        // Arbre : tours[0] = premier tour ... dernier tour = finale
        int nbTours = Integer.numberOfTrailingZeros(total);
        List<List<Match>> tours = new ArrayList<>();
        int taille = total / 2;
        for (int r = 0; r < nbTours; r++) {
            List<Match> tour = new ArrayList<>();
            for (int i = 0; i < taille; i++) {
                Match m = nouveauMatch(t, phaseParTaille(taille));
                if (r == 0) {
                    m.getEquipeMatchs().add(OutilsMatch.participation(m, 1, premierTour.get(i).a()));
                    m.getEquipeMatchs().add(OutilsMatch.participation(m, 2, premierTour.get(i).b()));
                } else {
                    m.getEquipeMatchs().add(OutilsMatch.participation(m, 1, null));
                    m.getEquipeMatchs().add(OutilsMatch.participation(m, 2, null));
                }
                tour.add(m);
            }
            tours.add(tour);
            taille /= 2;
        }
        for (int r = 0; r < nbTours - 1; r++) {
            for (int i = 0; i < tours.get(r).size(); i++) {
                Match m = tours.get(r).get(i);
                m.setMatchSuivantVictoire(tours.get(r + 1).get(i / 2));
                m.setSlotSuivant(i % 2 == 0 ? 1 : 2);
            }
        }
        Match finale = tours.get(nbTours - 1).get(0);
        Match petiteFinale = null;
        if (total >= 4) {
            petiteFinale = nouveauMatch(t, PhaseMatch.PETITE_FINALE);
            petiteFinale.getEquipeMatchs().add(OutilsMatch.participation(petiteFinale, 1, null));
            petiteFinale.getEquipeMatchs().add(OutilsMatch.participation(petiteFinale, 2, null));
            for (Match demi : tours.get(nbTours - 2)) demi.setMatchSuivantDefaite(petiteFinale);
        }

        // Ordre chronologique : tours, petite finale, finale
        List<Match> chronologie = new ArrayList<>();
        for (int r = 0; r < nbTours - 1; r++) chronologie.addAll(tours.get(r));
        if (petiteFinale != null) chronologie.add(petiteFinale);
        chronologie.add(finale);

        List<Match> existants = matchRepository.findByTournoiIdOrderByNumeroAsc(tournoiId);
        LocalDateTime apres = existants.stream().map(Match::getDateHeure).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        int numero = existants.stream().mapToInt(Match::getNumero).max().orElse(0) + 1;
        List<LocalDateTime> creneaux = planification.creneauxLibres(t, apres);
        if (creneaux.size() < chronologie.size()) {
            throw new RegleMetierException("Pas assez de créneaux pour la phase éliminatoire : "
                    + chronologie.size() + " nécessaires, " + creneaux.size() + " disponibles");
        }
        for (int i = 0; i < chronologie.size(); i++) {
            chronologie.get(i).setNumero(numero++);
            chronologie.get(i).setDateHeure(creneaux.get(i));
        }

        // Enregistrement du dernier match vers le premier (les liens pointent vers des matchs déjà enregistrés)
        matchRepository.save(finale);
        if (petiteFinale != null) matchRepository.save(petiteFinale);
        for (int r = nbTours - 2; r >= 0; r--) {
            for (Match m : tours.get(r)) matchRepository.save(m);
        }
        t.setStatut(StatutTournoi.PHASE_ELIMINATOIRE);
    }

    // ---- croisements ----

    /** 2 qualifiés par groupe : 1A–2B, 1C–2D... puis 1B–2A, 1D–2C... */
    private List<Affiche> affichesParGroupesAppaires(List<List<Equipe>> q) {
        List<Affiche> typeA = new ArrayList<>();
        List<Affiche> typeB = new ArrayList<>();
        for (int g = 0; g < q.size(); g += 2) {
            typeA.add(new Affiche(q.get(g).get(0), q.get(g + 1).get(1)));
            typeB.add(new Affiche(q.get(g + 1).get(0), q.get(g).get(1)));
        }
        typeA.addAll(typeB);
        return typeA;
    }

    /** Têtes de série : tous les premiers, puis tous les deuxièmes... ; tableau standard. */
    private List<Affiche> affichesParTetesDeSerie(List<List<Equipe>> q, int total) {
        int nbGroupes = q.size();
        int parGroupe = total / nbGroupes;
        List<Equipe> seeds = new ArrayList<>();
        for (int r = 0; r < parGroupe; r++) {
            for (int g = 0; g < nbGroupes; g++) seeds.add(q.get(g).get(r));
        }
        List<Integer> ordre = ordreTableau(total);
        List<Affiche> affiches = new ArrayList<>();
        for (int i = 0; i < total / 2; i++) {
            affiches.add(new Affiche(seeds.get(ordre.get(2 * i) - 1), seeds.get(ordre.get(2 * i + 1) - 1)));
        }
        return affiches;
    }

    /** Ordre standard : 2 → [1,2] ; 4 → [1,4,2,3] ; 8 → [1,8,4,5,2,7,3,6]. */
    static List<Integer> ordreTableau(int taille) {
        List<Integer> ordre = new ArrayList<>(List.of(1));
        while (ordre.size() < taille) {
            int s = ordre.size() * 2;
            List<Integer> suivant = new ArrayList<>();
            for (int p : ordre) {
                suivant.add(p);
                suivant.add(s + 1 - p);
            }
            ordre = suivant;
        }
        return ordre;
    }

    private Match nouveauMatch(Tournoi t, PhaseMatch phase) {
        Match m = new Match();
        m.setTournoi(t);
        m.setPhase(phase);
        m.setStatut(StatutMatch.PLANIFIE);
        return m;
    }

    private PhaseMatch phaseParTaille(int nbMatchs) {
        return switch (nbMatchs) {
            case 8 -> PhaseMatch.HUITIEMES;
            case 4 -> PhaseMatch.QUARTS;
            case 2 -> PhaseMatch.DEMI_FINALE;
            case 1 -> PhaseMatch.FINALE;
            default -> throw new IllegalStateException("Taille de tour inattendue : " + nbMatchs);
        };
    }
}