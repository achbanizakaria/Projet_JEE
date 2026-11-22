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
public class TirageService {

    private record Rencontre(Equipe a, Equipe b) {}
    private record Poule(Groupe groupe, Equipe a, Equipe b) {}

    private final ControleAcces acces;
    private final InscriptionRepository inscriptionRepository;
    private final JoueurRepository joueurRepository;
    private final GroupeRepository groupeRepository;
    private final MatchRepository matchRepository;
    private final PlanificationStade planification;
    private Random random = new Random();

    void setRandom(Random random) { this.random = random; }   // pour les tests

    @Transactional
    public void effectuerTirage(Long organisateurId, Long tournoiId) {
        Tournoi t = acces.tournoiDe(organisateurId, tournoiId);
        if (t.getStatut() != StatutTournoi.INSCRIPTION_CLOSE) {
            throw new RegleMetierException(
                    "Le tirage n'est possible qu'une fois, après la clôture des inscriptions");
        }
        List<Equipe> equipes = new ArrayList<>(
                inscriptionRepository.findByTournoiIdAndStatut(tournoiId, StatutInscription.VALIDEE)
                        .stream().map(Inscription::getEquipe).toList());
        int nbGroupes = t.getNbGroupes();
        int minimum = Math.max(2, t.getNbQualifiesParGroupe());
        if (equipes.size() / nbGroupes < minimum) {
            throw new RegleMetierException("Il faut au moins " + (minimum * nbGroupes)
                    + " équipes validées pour " + nbGroupes + " groupe(s) (" + equipes.size() + " actuellement)");
        }
        verifierEffectifs(equipes);

        // 1. Tirage et répartition équilibrée
        Collections.shuffle(equipes, random);
        List<Groupe> groupes = new ArrayList<>();
        List<List<Equipe>> composition = new ArrayList<>();
        for (int g = 0; g < nbGroupes; g++) {
            Groupe groupe = new Groupe();
            groupe.setNom(String.valueOf((char) ('A' + g)));
            groupe.setTournoi(t);
            groupes.add(groupe);
            composition.add(new ArrayList<>());
        }
        for (int i = 0; i < equipes.size(); i++) composition.get(i % nbGroupes).add(equipes.get(i));
        for (int g = 0; g < nbGroupes; g++) {
            for (Equipe e : composition.get(g)) {
                ClassementGroupe c = new ClassementGroupe();
                c.setGroupe(groupes.get(g));
                c.setEquipe(e);
                groupes.get(g).getClassements().add(c);
            }
        }

        // 2. Matchs de poule, puis vérification des créneaux (poules + phase éliminatoire)
        List<Poule> aJouer = genererPoules(groupes, composition);
        int total = nbGroupes * t.getNbQualifiesParGroupe();
        int necessaires = aJouer.size() + Regles.nbMatchsEliminatoires(total);
        List<LocalDateTime> creneaux = planification.creneauxLibres(t, null);
        if (creneaux.size() < necessaires) {
            throw new RegleMetierException("Pas assez de créneaux entre le " + t.getDateDebut() + " et le "
                    + t.getDateFin() + " : " + necessaires + " nécessaires (poules et phase éliminatoire), "
                    + creneaux.size() + " disponibles. Allongez la période.");
        }

        // 3. Enregistrement
        groupeRepository.saveAll(groupes);
        Map<Long, Integer> dernier = new HashMap<>();
        int numero = 1;
        for (int idx = 0; !aJouer.isEmpty(); idx++) {
            int choisi = 0;
            int meilleur = Integer.MAX_VALUE;
            for (int k = 0; k < aJouer.size(); k++) {
                Poule p = aJouer.get(k);
                int score = Math.max(dernier.getOrDefault(p.a().getId(), -1000),
                        dernier.getOrDefault(p.b().getId(), -1000));
                if (score < meilleur) { meilleur = score; choisi = k; }   // équipe la plus reposée
            }
            Poule p = aJouer.remove(choisi);
            dernier.put(p.a().getId(), idx);
            dernier.put(p.b().getId(), idx);

            Match m = new Match();
            m.setTournoi(t);
            m.setGroupe(p.groupe());
            m.setPhase(PhaseMatch.GROUPES);
            m.setStatut(StatutMatch.PLANIFIE);
            m.setNumero(numero++);
            m.setDateHeure(creneaux.get(idx));
            m.getEquipeMatchs().add(OutilsMatch.participation(m, 1, p.a()));
            m.getEquipeMatchs().add(OutilsMatch.participation(m, 2, p.b()));
            matchRepository.save(m);
        }
        t.setStatut(StatutTournoi.TIRAGE_EFFECTUE);
    }

    /** Annulation explicite, possible tant qu'aucun résultat n'est saisi. */
    @Transactional
    public void annulerTirage(Long organisateurId, Long tournoiId) {
        Tournoi t = acces.tournoiDe(organisateurId, tournoiId);
        if (t.getStatut() != StatutTournoi.TIRAGE_EFFECTUE) {
            throw new RegleMetierException(
                    "Annulation impossible : aucun tirage en attente, ou des résultats sont déjà saisis");
        }
        List<Match> matchs = matchRepository.findByTournoiIdOrderByNumeroAsc(tournoiId);
        for (Match m : matchs) {          // lever les clés étrangères avant les suppressions
            m.setGroupe(null);
            m.setMatchSuivantVictoire(null);
            m.setMatchSuivantDefaite(null);
        }
        matchRepository.flush();
        t.getMatchs().clear();
        t.getGroupes().clear();
        t.setStatut(StatutTournoi.INSCRIPTION_CLOSE);
    }

    // ---- outils ----

    private void verifierEffectifs(List<Equipe> equipes) {
        for (Equipe e : equipes) {
            if (joueurRepository.countByEquipeId(e.getId()) < Regles.EFFECTIF_MINIMUM
                    || joueurRepository.countByEquipeIdAndEstCapitaineTrue(e.getId()) != 1) {
                throw new RegleMetierException("L'équipe « " + e.getNom()
                        + " » n'a plus un effectif valide (6 joueurs minimum, un capitaine)");
            }
        }
    }

    /** Toutes les rencontres de poule, tour par tour et groupe par groupe. */
    private List<Poule> genererPoules(List<Groupe> groupes, List<List<Equipe>> composition) {
        List<List<List<Rencontre>>> toursParGroupe = new ArrayList<>();
        int maxTours = 0;
        for (List<Equipe> equipes : composition) {
            List<List<Rencontre>> tours = tourDeRonde(equipes);
            toursParGroupe.add(tours);
            maxTours = Math.max(maxTours, tours.size());
        }
        List<Poule> poules = new ArrayList<>();
        for (int r = 0; r < maxTours; r++) {
            for (int g = 0; g < groupes.size(); g++) {
                if (r < toursParGroupe.get(g).size()) {
                    for (Rencontre re : toursParGroupe.get(g).get(r)) {
                        poules.add(new Poule(groupes.get(g), re.a(), re.b()));
                    }
                }
            }
        }
        return poules;
    }

    /** Méthode du cercle : chaque équipe en affronte une fois chacune des autres. */
    static List<List<Rencontre>> tourDeRonde(List<Equipe> equipes) {
        List<Equipe> l = new ArrayList<>(equipes);
        if (l.size() % 2 == 1) l.add(null);          // exempt
        int n = l.size();
        List<List<Rencontre>> tours = new ArrayList<>();
        for (int r = 0; r < n - 1; r++) {
            List<Rencontre> tour = new ArrayList<>();
            for (int i = 0; i < n / 2; i++) {
                Equipe a = l.get(i);
                Equipe b = l.get(n - 1 - i);
                if (a != null && b != null) tour.add(new Rencontre(a, b));
            }
            tours.add(tour);
            l.add(1, l.remove(n - 1));               // rotation, première équipe fixe
        }
        return tours;
    }
}