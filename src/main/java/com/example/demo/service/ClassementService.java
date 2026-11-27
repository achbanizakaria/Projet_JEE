package com.example.demo.service;

import com.example.demo.dao.entities.*;
import com.example.demo.dao.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ClassementService {

    private static final Comparator<ClassementGroupe> CRITERES_GLOBAUX =
            Comparator.comparingInt(ClassementGroupe::getPoints).reversed()
                    .thenComparing(Comparator.comparingInt(ClassementGroupe::getDifference).reversed())
                    .thenComparing(Comparator.comparingInt(ClassementGroupe::getButsPour).reversed());

    private record Rencontre(long equipeA, long equipeB, int butsA, int butsB, int issueA) {}
    private record Calcul(List<ClassementGroupe> ordre, List<List<ClassementGroupe>> egalites) {}

    private final ClassementGroupeRepository classementRepository;
    private final GroupeRepository groupeRepository;
    private final MatchRepository matchRepository;
    private final TournoiRepository tournoiRepository;
    private final ControleAcces acces;
    private Random random = new Random();

    void setRandom(Random random) { this.random = random; }   // pour les tests

    /** Recalcul complet du groupe (idempotent). Efface les tirages de départage antérieurs. */
    @Transactional
    public void recalculer(Long groupeId) {
        calculer(groupeId, true);
    }

    /** Vrai si une égalité parfaite empêche de désigner les qualifiés (positions q et q+1 confondues). */
    @Transactional(readOnly = true)
    public boolean departageNecessaire(Long groupeId, int nbQualifies) {
        Calcul c = calculer(groupeId, false);
        for (List<ClassementGroupe> bloc : c.egalites()) {
            int min = Integer.MAX_VALUE;
            int max = -1;
            for (ClassementGroupe l : bloc) {
                min = Math.min(min, l.getRang() - 1);
                max = Math.max(max, l.getRang() - 1);
            }
            if (min < nbQualifies && max >= nbQualifies) return true;
        }
        return false;
    }

    /** Tirage au sort entre les équipes parfaitement à égalité (action de l'organisateur). */
    @Transactional
    public void departagerParTirage(Long organisateurId, Long groupeId) {
        Tournoi t = tournoiRepository.findByGroupesId(groupeId).orElseThrow(() -> new RegleMetierException("Groupe introuvable"));
        departagerParTirage(organisateurId, t.getId(), groupeId);
    }

    @Transactional
    public void departagerParTirage(Long organisateurId, Long tournoiId, Long groupeId) {
        Tournoi t = acces.modifierTournoi(organisateurId, tournoiId);
        Groupe groupe = groupeRepository.findById(groupeId)
                .orElseThrow(() -> new RegleMetierException("Groupe introuvable"));
        if (!groupe.getTournoi().getId().equals(tournoiId)) throw new AccesRefuseException("Groupe d’un autre tournoi");
        if (t.getStatut() != StatutTournoi.PHASE_GROUPES) {
            throw new RegleMetierException("Le départage n'est possible que pendant la phase de groupes");
        }
        if (matchRepository.countByTournoiIdAndPhaseAndStatutNotIn(tournoiId, PhaseMatch.GROUPES,
                List.of(StatutMatch.TERMINE, StatutMatch.FORFAIT)) > 0 || !departageNecessaire(groupeId, t.getNbQualifiesParGroupe())) {
            throw new RegleMetierException("Le départage concerne la dernière place qualificative, une fois les poules terminées");
        }
        Calcul c = calculer(groupeId, false);
        if (c.egalites().isEmpty()) {
            throw new RegleMetierException("Aucune égalité parfaite dans ce groupe");
        }
        for (List<ClassementGroupe> bloc : c.egalites()) {
            List<ClassementGroupe> melange = new ArrayList<>(bloc);
            Collections.shuffle(melange, random);
            for (int i = 0; i < melange.size(); i++) melange.get(i).setTirageSort(i + 1);
        }
        calculer(groupeId, false);
    }

    @Transactional(readOnly = true)
    public List<Equipe> qualifies(Long groupeId, int nbQualifies) {
        return classementRepository.findByGroupeIdOrderByRangAsc(groupeId).stream()
                .limit(nbQualifies).map(ClassementGroupe::getEquipe).toList();
    }

    // ---- calcul ----

    private Calcul calculer(Long groupeId, boolean reinitialiserTirage) {
        List<ClassementGroupe> lignes = classementRepository.findByGroupeId(groupeId);
        Map<Long, ClassementGroupe> parEquipe = new HashMap<>();
        for (ClassementGroupe l : lignes) {
            l.setPoints(0); l.setJoues(0); l.setGagnes(0); l.setNuls(0); l.setPerdus(0);
            l.setButsPour(0); l.setButsContre(0); l.setDifference(0); l.setFairPlay(0);
            if (reinitialiserTirage) l.setTirageSort(null);
            parEquipe.put(l.getEquipe().getId(), l);
        }
        List<Rencontre> rencontres = new ArrayList<>();
        for (Match m : matchRepository.findByGroupeId(groupeId)) {
            if (!compte(m)) continue;
            EquipeMatch a = m.getEquipeMatchs().get(0);
            EquipeMatch b = m.getEquipeMatchs().get(1);
            ClassementGroupe la = parEquipe.get(a.getEquipe().getId());
            ClassementGroupe lb = parEquipe.get(b.getEquipe().getId());
            if (la == null || lb == null) {
                throw new IllegalStateException("Équipe absente du classement du groupe");
            }
            int issueA = Integer.compare(a.getScore(), b.getScore());
            if (m.getStatut() == StatutMatch.FORFAIT) issueA = a.isForfait() ? -1 : 1;
            appliquer(la, a.getScore(), b.getScore(), issueA, penalites(a));
            appliquer(lb, b.getScore(), a.getScore(), -issueA, penalites(b));
            rencontres.add(new Rencontre(a.getEquipe().getId(), b.getEquipe().getId(),
                    a.getScore(), b.getScore(), issueA));
        }
        for (ClassementGroupe l : lignes) l.setDifference(l.getButsPour() - l.getButsContre());

        List<List<ClassementGroupe>> egalites = new ArrayList<>();
        List<ClassementGroupe> ordre = ordonner(lignes, rencontres, egalites);
        for (int i = 0; i < ordre.size(); i++) ordre.get(i).setRang(i + 1);
        return new Calcul(ordre, egalites);
    }

    /** Un match compte s'il est terminé ou attribué par forfait (sauf double forfait). */
    private boolean compte(Match m) {
        if (m.getEquipeMatchs().size() != 2) return false;
        EquipeMatch a = m.getEquipeMatchs().get(0);
        EquipeMatch b = m.getEquipeMatchs().get(1);
        if (a.getEquipe() == null || b.getEquipe() == null) return false;
        if (m.getStatut() == StatutMatch.TERMINE) return true;
        return m.getStatut() == StatutMatch.FORFAIT && !(a.isForfait() && b.isForfait());
    }

    private void appliquer(ClassementGroupe l, int pour, int contre, int issue, int fairPlay) {
        l.setJoues(l.getJoues() + 1);
        l.setButsPour(l.getButsPour() + pour);
        l.setButsContre(l.getButsContre() + contre);
        if (issue > 0) {
            l.setGagnes(l.getGagnes() + 1);
            l.setPoints(l.getPoints() + Regles.POINTS_VICTOIRE);
        } else if (issue == 0) {
            l.setNuls(l.getNuls() + 1);
            l.setPoints(l.getPoints() + Regles.POINTS_NUL);
        } else {
            l.setPerdus(l.getPerdus() + 1);
        }
        l.setFairPlay(l.getFairPlay() + fairPlay);
    }

    private static int penalites(EquipeMatch em) {
        int total = 0;
        for (JoueurMatch jm : em.getJoueurs()) total += penalite(jm);
        return total;
    }

    /** Barème fair-play : jaune −1, deux jaunes −3, rouge direct −4, jaune puis rouge direct −5. */
    static int penalite(JoueurMatch jm) {
        if (jm.isCartonRouge()) return jm.getCartonsJaunes() == 1 ? -5 : -4;
        return switch (jm.getCartonsJaunes()) {
            case 1 -> -1;
            case 2 -> -3;
            default -> 0;
        };
    }

    // ---- ordre : points, différence, buts, confrontation directe, fair-play, tirage ----

    private List<ClassementGroupe> ordonner(List<ClassementGroupe> lignes, List<Rencontre> rencontres,
                                            List<List<ClassementGroupe>> egalites) {
        List<ClassementGroupe> tries = new ArrayList<>(lignes);
        tries.sort(CRITERES_GLOBAUX);
        List<ClassementGroupe> resultat = new ArrayList<>();
        for (List<ClassementGroupe> bloc : blocs(tries, CRITERES_GLOBAUX)) {
            resultat.addAll(departager(bloc, rencontres, egalites));
        }
        return resultat;
    }

    /** Confrontation directe récursive sur le sous-groupe à égalité, puis fair-play, puis tirage. */
    private List<ClassementGroupe> departager(List<ClassementGroupe> bloc, List<Rencontre> rencontres,
                                              List<List<ClassementGroupe>> egalites) {
        if (bloc.size() == 1) return bloc;
        Map<Long, int[]> mini = new HashMap<>();     // [points, différence, buts] entre les équipes du bloc
        for (ClassementGroupe l : bloc) mini.put(l.getEquipe().getId(), new int[3]);
        for (Rencontre r : rencontres) {
            int[] a = mini.get(r.equipeA());
            int[] b = mini.get(r.equipeB());
            if (a == null || b == null) continue;
            cumuler(a, r.butsA(), r.butsB(), r.issueA());
            cumuler(b, r.butsB(), r.butsA(), -r.issueA());
        }
        Comparator<ClassementGroupe> parConfrontation = (x, y) -> {
            int[] mx = mini.get(x.getEquipe().getId());
            int[] my = mini.get(y.getEquipe().getId());
            for (int i = 0; i < 3; i++) {
                if (mx[i] != my[i]) return Integer.compare(my[i], mx[i]);
            }
            return 0;
        };
        List<ClassementGroupe> tries = new ArrayList<>(bloc);
        tries.sort(parConfrontation);
        List<List<ClassementGroupe>> sousBlocs = blocs(tries, parConfrontation);
        if (sousBlocs.size() == 1) return fairPlayPuisTirage(tries, egalites);
        List<ClassementGroupe> resultat = new ArrayList<>();
        for (List<ClassementGroupe> sb : sousBlocs) resultat.addAll(departager(sb, rencontres, egalites));
        return resultat;
    }

    private static void cumuler(int[] t, int pour, int contre, int issue) {
        t[0] += issue > 0 ? Regles.POINTS_VICTOIRE : (issue == 0 ? Regles.POINTS_NUL : 0);
        t[1] += pour - contre;
        t[2] += pour;
    }

    private List<ClassementGroupe> fairPlayPuisTirage(List<ClassementGroupe> bloc,
                                                      List<List<ClassementGroupe>> egalites) {
        Comparator<ClassementGroupe> cmp = Comparator.comparingInt(ClassementGroupe::getFairPlay).reversed()
                .thenComparing(ClassementGroupe::getTirageSort,
                        Comparator.nullsLast(Comparator.<Integer>naturalOrder()));
        List<ClassementGroupe> tries = new ArrayList<>(bloc);
        tries.sort(cmp);
        for (List<ClassementGroupe> sb : blocs(tries, cmp)) {
            if (sb.size() > 1) egalites.add(sb);     // égalité parfaite : il faut un tirage
        }
        return tries;
    }

    private static List<List<ClassementGroupe>> blocs(List<ClassementGroupe> tries,
                                                      Comparator<ClassementGroupe> cmp) {
        List<List<ClassementGroupe>> blocs = new ArrayList<>();
        for (ClassementGroupe l : tries) {
            if (blocs.isEmpty() || cmp.compare(blocs.get(blocs.size() - 1).get(0), l) != 0) {
                blocs.add(new ArrayList<>());
            }
            blocs.get(blocs.size() - 1).add(l);
        }
        return blocs;
    }
}
