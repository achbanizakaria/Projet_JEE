package com.example.demo.service;

import com.example.demo.dao.entities.*;
import com.example.demo.dao.repositories.*;
import com.example.demo.dto.CompositionForm;
import com.example.demo.dto.JoueurStatForm;
import com.example.demo.dto.ResultatForm;
import com.example.demo.dto.Vues.ActionsMatch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class MatchService {

    private static final Set<StatutTournoi> EN_COURS = EnumSet.of(
            StatutTournoi.TIRAGE_EFFECTUE, StatutTournoi.PHASE_GROUPES, StatutTournoi.PHASE_ELIMINATOIRE);

    private final MatchRepository matchRepository;
    private final InscriptionRepository inscriptionRepository;
    private final ClassementService classementService;
    private final ControleAcces acces;
    private final Clock horloge;
    private final TournoiRepository tournoiRepository;

    @Transactional(readOnly = true)
    public ActionsMatch actions(Long acteurId, boolean organisateur, Long matchId) {
        Match m = trouver(matchId);
        boolean responsable = organisateur && m.getTournoi().getOrganisateur().getId().equals(acteurId);
        int cote = 0;
        if (!organisateur) {
            Joueur j = acces.joueur(acteurId);
            if (j.isEstCapitaine() && j.getEquipe() != null) {
                cote = OutilsMatch.deEquipe(m, j.getEquipe().getId()).map(EquipeMatch::getOrdre).orElse(0);
            }
        }
        EquipeMatch a = m.getEquipeMatchs().get(0);
        EquipeMatch b = m.getEquipeMatchs().get(1);
        boolean connus = a.getEquipe() != null && b.getEquipe() != null;
        return new ActionsMatch(responsable, cote, cote != 0 && compositionOuverte(m),
                responsable && preparableParOrganisateur(a), responsable && preparableParOrganisateur(b),
                responsable ? blocageResultat(m) : null,
                connus && (responsable || cote == 1) && forfaitPossible(m, a),
                connus && (responsable || cote == 2) && forfaitPossible(m, b));
    }

    @Transactional(readOnly = true)
    public CompositionForm formulaireComposition(Long capitaineId, Long matchId) {
        return composition(coteDuCapitaine(trouver(matchId), capitaineId));
    }

    @Transactional(readOnly = true)
    public CompositionForm compositionOrganisateur(Long responsableId, Long matchId, Long equipeId) {
        return composition(coteDeOrganisateur(trouver(matchId), responsableId, equipeId));
    }

    private CompositionForm composition(EquipeMatch cote) {
        CompositionForm form = new CompositionForm();
        for (JoueurMatch ligne : cote.getJoueurs()) {
            if (ligne.isEstTitulaire()) form.getTitulaires().add(ligne.getJoueur().getId());
            else form.setRemplacant(ligne.getJoueur().getId());
        }
        return form;
    }

    private EquipeMatch coteDuCapitaine(Match m, Long capitaineId) {
        Joueur capitaine = acces.capitaine(capitaineId);
        EquipeMatch cote = OutilsMatch.deEquipe(m, capitaine.getEquipe().getId())
                .orElseThrow(() -> new AccesRefuseException("Votre équipe ne joue pas ce match"));
        if (!compositionOuverte(m)) {
            throw new RegleMetierException("Composition impossible : le match n'est plus planifié");
        }
        return cote;
    }

    private boolean compositionOuverte(Match match) {
        return match.getStatut() == StatutMatch.PLANIFIE && EN_COURS.contains(match.getTournoi().getStatut()) && !poulesVerrouillees(match);
    }

    private boolean preparableParOrganisateur(EquipeMatch em) {
        return em.getEquipe() != null && compositionOuverte(em.getMatch()) && (em.getJoueurs().isEmpty() || em.isCompositionOrganisateur());
    }

    private EquipeMatch coteDeOrganisateur(Match m, Long responsableId, Long equipeId) {
        acces.tournoiDe(responsableId, m.getTournoi().getId());
        EquipeMatch em = OutilsMatch.deEquipe(m, equipeId)
                .orElseThrow(() -> new RegleMetierException("Cette équipe ne joue pas ce match"));
        if (!compositionOuverte(m)) throw new RegleMetierException("La composition de ce match est fermée");
        if (!preparableParOrganisateur(em)) throw new RegleMetierException("Le capitaine a déjà défini cette composition");
        return em;
    }

    // ================= UC07 : composition =================

    @Transactional
    public void definirComposition(Long capitaineId, Long matchId, CompositionForm f) {
        Match m = pourEcriture(matchId);
        EquipeMatch em = coteDuCapitaine(m, capitaineId);
        enregistrerComposition(em, f);
        em.setCompositionOrganisateur(false);
    }

    @Transactional
    public void preparerComposition(Long responsableId, Long matchId, Long equipeId, CompositionForm f) {
        EquipeMatch em = coteDeOrganisateur(pourEcriture(matchId), responsableId, equipeId);
        enregistrerComposition(em, f);
        em.setCompositionOrganisateur(true);
    }

    private void enregistrerComposition(EquipeMatch em, CompositionForm f) {
        Long equipeId = em.getEquipe().getId();

        List<Long> titulaires = f.getTitulaires() == null ? List.of() : f.getTitulaires();
        Set<Long> distincts = new HashSet<>(titulaires);
        if (distincts.contains(null) || titulaires.size() != Regles.NB_TITULAIRES || distincts.size() != Regles.NB_TITULAIRES) {
            throw new RegleMetierException("Il faut exactement " + Regles.NB_TITULAIRES + " titulaires différents");
        }
        Long remplacant = f.getRemplacant();
        if (remplacant != null && distincts.contains(remplacant)) {
            throw new RegleMetierException("Le remplaçant ne peut pas être aussi titulaire");
        }
        List<Long> tous = new ArrayList<>(titulaires);
        if (remplacant != null) tous.add(remplacant);
        for (Long id : tous) {
            Joueur j = acces.joueur(id);
            if (j.getEquipe() == null || !j.getEquipe().getId().equals(equipeId)) {
                throw new RegleMetierException("Un joueur choisi n'appartient pas à votre équipe");
            }
        }
        em.getJoueurs().clear();
        matchRepository.flush();             // supprimer l'ancienne composition avant d'insérer la nouvelle
        for (Long id : titulaires) em.getJoueurs().add(ligne(em, id, true));
        if (remplacant != null) em.getJoueurs().add(ligne(em, remplacant, false));
    }

    // ================= UC14 : résultat =================

    /**
     * Formulaire de résultat pré-rempli avec l'état actuel du match, dont la composition
     * définie par les capitaines : l'organisateur n'a plus qu'à saisir score, buts et cartons.
     */
    @Transactional(readOnly = true)
    public ResultatForm formulaireResultat(Long organisateurId, Long matchId) {
        Match m = trouver(matchId);
        acces.tournoiDe(organisateurId, m.getTournoi().getId());
        verifierSaisiePossible(m);
        EquipeMatch e1 = m.getEquipeMatchs().get(0);
        EquipeMatch e2 = m.getEquipeMatchs().get(1);
        ResultatForm f = new ResultatForm();
        f.setScoreEquipe1(e1.getScore());
        f.setScoreEquipe2(e2.getScore());
        f.setTirsAuBut1(e1.getTirsAuBut());
        f.setTirsAuBut2(e2.getTirsAuBut());
        f.setJoueursEquipe1(lignes(e1));
        f.setJoueursEquipe2(lignes(e2));
        return f;
    }

    @Transactional
    public void enregistrerResultat(Long organisateurId, Long matchId, ResultatForm f) {
        Match m = pourEcriture(matchId);
        Tournoi t = acces.tournoiDe(organisateurId, m.getTournoi().getId());
        verifierSaisiePossible(m);
        boolean eliminatoire = m.getPhase() != PhaseMatch.GROUPES;
        EquipeMatch e1 = m.getEquipeMatchs().get(0);
        EquipeMatch e2 = m.getEquipeMatchs().get(1);
        if (f.getScoreEquipe1() == null || f.getScoreEquipe2() == null || f.getScoreEquipe1() < 0 || f.getScoreEquipe2() < 0) {
            throw new RegleMetierException("Renseignez deux scores positifs ou nuls");
        }

        int s1 = f.getScoreEquipe1();
        int s2 = f.getScoreEquipe2();
        boolean egalite = s1 == s2;
        if (eliminatoire && egalite) {
            Integer p1 = f.getTirsAuBut1();
            Integer p2 = f.getTirsAuBut2();
            if (p1 == null || p2 == null || p1 < 0 || p2 < 0 || p1.equals(p2)) {
                throw new RegleMetierException("Score nul en phase éliminatoire : saisissez des tirs au but avec un vainqueur");
            }
        }
        verifierFeuille(e1, s1, f.getJoueursEquipe1());
        verifierFeuille(e2, s2, f.getJoueursEquipe2());

        e1.setScore(s1);
        e2.setScore(s2);
        e1.setTirsAuBut(eliminatoire && egalite ? f.getTirsAuBut1() : null);
        e2.setTirsAuBut(eliminatoire && egalite ? f.getTirsAuBut2() : null);
        remplacerFeuilles(e1, f.getJoueursEquipe1(), e2, f.getJoueursEquipe2());
        m.setStatut(StatutMatch.TERMINE);
        passerEnPhaseGroupes(t);
        terminer(m);
    }

    // ================= UC08 : forfaits =================

    @Transactional
    public void declarerForfait(Long capitaineId, Long matchId, String motif) {
        Match m = pourEcriture(matchId);
        Joueur capitaine = acces.capitaine(capitaineId);
        EquipeMatch fautif = OutilsMatch.deEquipe(m, capitaine.getEquipe().getId())
                .orElseThrow(() -> new AccesRefuseException("Votre équipe ne joue pas ce match"));
        forfaitDeMatch(m, fautif, motif);
    }

    @Transactional
    public void declarerForfaitParOrganisateur(Long organisateurId, Long matchId, Long equipeId, String motif) {
        Match m = pourEcriture(matchId);
        acces.tournoiDe(organisateurId, m.getTournoi().getId());
        EquipeMatch fautif = OutilsMatch.deEquipe(m, equipeId)
                .orElseThrow(() -> new RegleMetierException("Cette équipe ne joue pas ce match"));
        forfaitDeMatch(m, fautif, motif);
    }

    /** Retrait du tournoi. Avant le tirage : l'inscription passe à FORFAIT. Après : tous les matchs restants aussi. */
    @Transactional
    public void declarerForfaitGeneral(Long capitaineId, Long tournoiId, String motif) {
        Joueur capitaine = acces.capitaine(capitaineId);
        Long equipeId = capitaine.getEquipe().getId();
        Inscription inscription = inscriptionRepository.findByTournoiIdAndEquipeId(tournoiId, equipeId)
                .orElseThrow(() -> new RegleMetierException("Votre équipe n'est pas inscrite à ce tournoi"));
        if (inscription.getStatut() != StatutInscription.VALIDEE) {
            throw new RegleMetierException("Seule une équipe validée peut déclarer forfait général");
        }
        Tournoi t = inscription.getTournoi();
        switch (t.getStatut()) {
            case INSCRIPTION_OUVERTE, INSCRIPTION_CLOSE -> inscription.setStatut(StatutInscription.FORFAIT);
            case TIRAGE_EFFECTUE, PHASE_GROUPES -> {
                inscription.setStatut(StatutInscription.FORFAIT);
                Set<Long> groupes = new LinkedHashSet<>();
                for (Match m : matchRepository
                        .findDistinctByTournoiIdAndEquipeMatchsEquipeIdOrderByNumeroAsc(tournoiId, equipeId)) {
                    EquipeMatch fautif = OutilsMatch.deEquipe(m, equipeId).orElseThrow();
                    if (!forfaitPossible(m, fautif)) continue;
                    appliquerForfait(m, fautif, motif);
                    groupes.add(m.getGroupe().getId());
                }
                passerEnPhaseGroupes(t);
                groupes.forEach(classementService::recalculer);
            }
            default -> throw new RegleMetierException(
                    "Forfait général impossible à ce stade : déclarez le forfait match par match");
        }
    }

    // ================= outils =================

    private void forfaitDeMatch(Match m, EquipeMatch fautif, String motif) {
        verifierTournoiEnCours(m.getTournoi());
        if (!forfaitPossible(m, fautif)) {
            throw new RegleMetierException("Forfait impossible : le match a déjà commencé ou est terminé");
        }
        appliquerForfait(m, fautif, motif);
        passerEnPhaseGroupes(m.getTournoi());
        terminer(m);
    }

    private boolean forfaitPossible(Match m, EquipeMatch fautif) {
        if (!EN_COURS.contains(m.getTournoi().getStatut()) || poulesVerrouillees(m)) return false;
        if (m.getStatut() == StatutMatch.PLANIFIE) return true;
        // second forfait sur un match de poule déjà attribué par forfait
        return m.getStatut() == StatutMatch.FORFAIT && m.getPhase() == PhaseMatch.GROUPES
                && !fautif.isForfait() && OutilsMatch.adversaire(m, fautif).isForfait();
    }

    private void appliquerForfait(Match m, EquipeMatch fautif, String motif) {
        if (motif == null || motif.isBlank() || motif.strip().length() > 255) {
            throw new RegleMetierException("Indiquez un motif de forfait de 1 à 255 caractères");
        }
        EquipeMatch autre = OutilsMatch.adversaire(m, fautif);
        if (autre.getEquipe() == null) {
            throw new RegleMetierException("L'adversaire n'est pas encore connu : forfait impossible");
        }
        boolean secondForfait = autre.isForfait();
        if (secondForfait && m.getPhase() != PhaseMatch.GROUPES) {
            throw new RegleMetierException("Double forfait impossible en phase éliminatoire");
        }
        fautif.setForfait(true);
        fautif.setMotifForfait(motif.trim());
        fautif.setScore(0);
        fautif.setTirsAuBut(null);
        fautif.getJoueurs().clear();
        autre.setScore(secondForfait ? 0 : m.getTournoi().getScoreForfait());
        m.setStatut(StatutMatch.FORFAIT);
    }

    /** Après un résultat ou un forfait : classement du groupe, ou placement dans l'arbre. */
    private void terminer(Match m) {
        if (m.getPhase() == PhaseMatch.GROUPES) {
            classementService.recalculer(m.getGroupe().getId());
        } else {
            propager(m);
        }
    }

    private void propager(Match m) {
        EquipeMatch v = OutilsMatch.vainqueur(m)
                .orElseThrow(() -> new RegleMetierException("Vainqueur indéterminé"));
        EquipeMatch p = OutilsMatch.perdant(m)
                .orElseThrow(() -> new RegleMetierException("Perdant indéterminé"));
        placer(m.getMatchSuivantVictoire(), m.getSlotSuivant(), v.getEquipe());
        placer(m.getMatchSuivantDefaite(), m.getSlotSuivant(), p.getEquipe());
    }

    private void placer(Match suivant, Integer slot, Equipe equipe) {
        if (suivant == null || slot == null) return;
        EquipeMatch cible = suivant.getEquipeMatchs().stream()
                .filter(em -> em.getOrdre() == slot.intValue()).findFirst()
                .orElseThrow(() -> new IllegalStateException("Emplacement introuvable dans le match suivant"));
        if (cible.getEquipe() != null && !cible.getEquipe().getId().equals(equipe.getId())) {
            cible.getJoueurs().clear();          // la composition de l'ancienne équipe n'a plus de sens
        }
        cible.setEquipe(equipe);
    }

    private boolean poulesVerrouillees(Match m) {
        return m.getPhase() == PhaseMatch.GROUPES && m.getTournoi().getStatut() == StatutTournoi.PHASE_ELIMINATOIRE;
    }

    private void verifierSaisiePossible(Match m) {
        String raison = blocageResultat(m);
        if (raison != null) throw new RegleMetierException(raison);
    }

    private String blocageResultat(Match m) {
        if (!EN_COURS.contains(m.getTournoi().getStatut())) return "Le tournoi n’est plus en cours de jeu.";
        if (poulesVerrouillees(m)) return "Les résultats de poule sont verrouillés depuis la génération du tableau.";
        if (m.getStatut() == StatutMatch.FORFAIT) return "Ce match a été attribué par forfait.";
        if (m.getEquipeMatchs().stream().anyMatch(e -> e.getEquipe() == null)) return "Les deux équipes ne sont pas encore connues.";
        if (m.getStatut() == StatutMatch.TERMINE) {
            for (Match suivant : Arrays.asList(m.getMatchSuivantVictoire(), m.getMatchSuivantDefaite())) {
                if (suivant != null && suivant.getStatut() != StatutMatch.PLANIFIE) return "Le match suivant a déjà été joué.";
            }
            return null;
        }
        if (m.getDateHeure() == null) return "Le match n’a pas encore de créneau.";
        var precedent = matchRepository.findFirstByTournoiIdAndStatutInAndDateHeureLessThanOrderByDateHeureAscNumeroAsc(
                m.getTournoi().getId(), List.of(StatutMatch.PLANIFIE, StatutMatch.EN_COURS), m.getDateHeure());
        if (precedent.isPresent()) return "Terminez d’abord le match n° " + precedent.get().getNumero() + " de ce tournoi.";
        LocalDateTime fin = m.getDateHeure().plusMinutes(m.getTournoi().getDureeMatchMinutes());
        if (LocalDateTime.now(horloge).isBefore(fin)) {
            return "Saisie du résultat à partir du " + fin.format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm")) + ".";
        }
        return null;
    }

    private void verifierFeuille(EquipeMatch em, int score, List<JoueurStatForm> lignes) {
        String equipe = em.getEquipe().getNom();
        List<JoueurStatForm> feuille = lignes == null ? List.of() : lignes;
        Set<Long> vus = new HashSet<>();
        int titulaires = 0, entrees = 0, sorties = 0;
        long sommeButs = 0;
        Integer minuteEntree = null, minuteSortie = null;
        for (JoueurStatForm l : feuille) {
            if (l == null || l.getJoueurId() == null) throw new RegleMetierException(equipe + " : choisissez chaque joueur de la feuille");
            int duree = em.getMatch().getTournoi().getDureeMatchMinutes();
            if (minuteInvalide(l.getMinuteEntree(), duree) || minuteInvalide(l.getMinuteSortie(), duree)) {
                throw new RegleMetierException(equipe + " : les minutes doivent être comprises entre 0 et " + duree);
            }
            if (!vus.add(l.getJoueurId())) {
                throw new RegleMetierException(equipe + " : joueur en double dans la feuille de match");
            }
            Joueur j = acces.joueur(l.getJoueurId());
            if (j.getEquipe() == null || !j.getEquipe().getId().equals(em.getEquipe().getId())) {
                throw new RegleMetierException(equipe + " : " + j.getNom() + " n'appartient pas à l'équipe");
            }
            if (l.getButs() < 0 || l.getCartonsJaunes() < 0 || l.getCartonsJaunes() > 2
                    || (l.isCartonRouge() && l.getCartonsJaunes() == 2)) {
                throw new RegleMetierException(equipe + " : buts ou cartons incohérents pour " + j.getNom());
            }
            if (l.isEstTitulaire()) {
                titulaires++;
                if (l.getMinuteEntree() != null) {
                    throw new RegleMetierException(equipe + " : un titulaire n'a pas de minute d'entrée");
                }
            } else {
                if (l.getMinuteSortie() != null) {
                    throw new RegleMetierException(equipe + " : un remplaçant n'a pas de minute de sortie");
                }
                if (l.getButs() > 0 && l.getMinuteEntree() == null) {
                    throw new RegleMetierException(equipe + " : un remplaçant non entré ne peut pas marquer");
                }
            }
            if (l.getMinuteEntree() != null) { entrees++; minuteEntree = l.getMinuteEntree(); }
            if (l.getMinuteSortie() != null) { sorties++; minuteSortie = l.getMinuteSortie(); }
            sommeButs += l.getButs();
        }
        if (titulaires != Regles.NB_TITULAIRES) {
            throw new RegleMetierException(equipe + " : il faut " + Regles.NB_TITULAIRES + " titulaires");
        }
        if (feuille.size() > Regles.NB_TITULAIRES + Regles.NB_REMPLACANTS_MAX) {
            throw new RegleMetierException(equipe + " : " + (Regles.NB_TITULAIRES + Regles.NB_REMPLACANTS_MAX)
                    + " joueurs au maximum");
        }
        if (entrees > 1 || entrees != sorties
                || (minuteEntree != null && !minuteEntree.equals(minuteSortie))) {
            throw new RegleMetierException(equipe + " : un seul changement au maximum, avec la même minute de sortie et d'entrée");
        }
        if (sommeButs != score) {
            throw new RegleMetierException(equipe + " : le total des buts des joueurs (" + sommeButs
                    + ") diffère du score (" + score + ")");
        }
        verifierComposition(em, feuille, equipe);
    }

    /** Si le capitaine a défini une composition, la feuille de match doit la reprendre à l'identique. */
    private void verifierComposition(EquipeMatch em, List<JoueurStatForm> feuille, String equipe) {
        if (em.getJoueurs().isEmpty()) return;
        Map<Long, Boolean> composition = new HashMap<>();
        for (JoueurMatch jm : em.getJoueurs()) composition.put(jm.getJoueur().getId(), jm.isEstTitulaire());
        boolean identique = composition.size() == feuille.size()
                && feuille.stream().allMatch(l -> Objects.equals(composition.get(l.getJoueurId()), l.isEstTitulaire()));
        if (!identique) {
            throw new RegleMetierException(equipe
                    + " : la feuille de match doit reprendre la composition enregistrée");
        }
    }

    private void remplacerFeuilles(EquipeMatch e1, List<JoueurStatForm> l1,
                                   EquipeMatch e2, List<JoueurStatForm> l2) {
        e1.getJoueurs().clear();
        e2.getJoueurs().clear();
        matchRepository.flush();             // supprimer avant d'insérer (contraintes d'unicité)
        ajouter(e1, l1);
        ajouter(e2, l2);
    }

    private void ajouter(EquipeMatch em, List<JoueurStatForm> lignes) {
        for (JoueurStatForm l : lignes) {
            JoueurMatch jm = ligne(em, l.getJoueurId(), l.isEstTitulaire());
            jm.setButs(l.getButs());
            jm.setCartonsJaunes(l.getCartonsJaunes());
            jm.setCartonRouge(l.isCartonRouge());
            jm.setMinuteEntree(l.getMinuteEntree());
            jm.setMinuteSortie(l.getMinuteSortie());
            em.getJoueurs().add(jm);
        }
    }

    private List<JoueurStatForm> lignes(EquipeMatch em) {
        List<JoueurStatForm> lignes = new ArrayList<>();
        for (JoueurMatch jm : em.getJoueurs()) {
            JoueurStatForm l = new JoueurStatForm();
            l.setJoueurId(jm.getJoueur().getId());
            l.setEstTitulaire(jm.isEstTitulaire());
            l.setButs(jm.getButs());
            l.setCartonsJaunes(jm.getCartonsJaunes());
            l.setCartonRouge(jm.isCartonRouge());
            l.setMinuteEntree(jm.getMinuteEntree());
            l.setMinuteSortie(jm.getMinuteSortie());
            lignes.add(l);
        }
        lignes.sort(Comparator.comparing(JoueurStatForm::isEstTitulaire).reversed().thenComparing(JoueurStatForm::getJoueurId));
        return lignes;
    }

    private JoueurMatch ligne(EquipeMatch em, Long joueurId, boolean titulaire) {
        JoueurMatch jm = new JoueurMatch();
        jm.setEquipeMatch(em);
        jm.setJoueur(acces.joueur(joueurId));
        jm.setEstTitulaire(titulaire);
        return jm;
    }

    private void verifierTournoiEnCours(Tournoi t) {
        if (!EN_COURS.contains(t.getStatut())) {
            throw new RegleMetierException("Le tournoi n'est pas en cours de jeu");
        }
    }

    private boolean minuteInvalide(Integer minute, int duree) {
        return minute != null && (minute < 0 || minute > duree);
    }

    private Match pourEcriture(Long matchId) {
        tournoiRepository.findByMatchsId(matchId).orElseThrow(() -> new RegleMetierException("Match introuvable"));
        return trouver(matchId);
    }

    private void passerEnPhaseGroupes(Tournoi t) {
        if (t.getStatut() == StatutTournoi.TIRAGE_EFFECTUE) t.setStatut(StatutTournoi.PHASE_GROUPES);
    }

    private Match trouver(Long matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> new RegleMetierException("Match introuvable"));
    }
}
