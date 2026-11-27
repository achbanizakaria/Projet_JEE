package com.example.demo.service;

import com.example.demo.dao.entities.*;
import com.example.demo.dao.repositories.*;
import com.example.demo.dto.Vues.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultationService {

    private final TournoiRepository tournoiRepository;
    private final MatchRepository matchRepository;
    private final GroupeRepository groupeRepository;
    private final ClassementGroupeRepository classementRepository;
    private final InscriptionRepository inscriptionRepository;
    private final EquipeMatchRepository equipeMatchRepository;
    private final JoueurRepository joueurRepository;
    private final ControleAcces acces;

    public List<TournoiResume> tournoisOuverts() {
        return tournoiRepository.findByStatutOrderByDateDebutDesc(StatutTournoi.INSCRIPTION_OUVERTE)
                .stream().map(this::resume).toList();
    }

    /** Tournois non terminés, le plus récent en premier : proposé par défaut. */
    public List<TournoiResume> tournoisEnCours() {
        return tournoiRepository.findByStatutNotOrderByDateDebutDesc(StatutTournoi.TERMINE)
                .stream().map(this::resume).toList();
    }
    public TournoiResume tournoi(Long tournoiId) {
        return tournoiRepository.findById(tournoiId).map(this::resume)
                .orElseThrow(() -> new RegleMetierException("Tournoi introuvable"));
    }

    public List<MatchVue> calendrier(Long tournoiId) {
        return matchRepository.findByTournoiIdOrderByNumeroAsc(tournoiId).stream().map(this::vue).toList();
    }

    public List<MatchVue> tableauEliminatoire(Long tournoiId) {
        return matchRepository.findByTournoiIdOrderByNumeroAsc(tournoiId).stream()
                .filter(m -> m.getPhase() != PhaseMatch.GROUPES).map(this::vue).toList();
    }

    public List<GroupeVue> classements(Long tournoiId) {
        return groupeRepository.findByTournoiIdOrderByNomAsc(tournoiId).stream().map(g ->
                new GroupeVue(g.getId(), g.getNom(),
                        classementRepository.findByGroupeIdOrderByRangAsc(g.getId()).stream()
                                .map(c -> new LigneClassement(c.getRang() == null ? 0 : c.getRang(),
                                        c.getEquipe().getNom(), c.getJoues(), c.getGagnes(), c.getNuls(),
                                        c.getPerdus(), c.getButsPour(), c.getButsContre(),
                                        c.getDifference(), c.getPoints(), c.getFairPlay()))
                                .toList())).toList();
    }

    public List<TournoiTermine> historique() {
        return tournoiRepository.findByStatutOrderByDateDebutDesc(StatutTournoi.TERMINE).stream().map(t ->
                new TournoiTermine(t.getId(), t.getNom(), t.getSport(), t.getDateDebut(), t.getDateFin(),
                        nomAuRang(t.getId(), 1), nomAuRang(t.getId(), 2))).toList();
    }

    public DetailMatch detailMatch(Long matchId) {
        Match m = matchRepository.findById(matchId)
                .orElseThrow(() -> new RegleMetierException("Match introuvable"));
        EquipeMatch a = m.getEquipeMatchs().get(0);
        EquipeMatch b = m.getEquipeMatchs().get(1);
        return new DetailMatch(m.getTournoi().getId(), vue(m), feuille(a), feuille(b),
                a.getEquipe() == null ? null : a.getEquipe().getId(), b.getEquipe() == null ? null : b.getEquipe().getId(),
                a.getMotifForfait(), b.getMotifForfait(), m.getTournoi().getDureeMatchMinutes());
    }

    public Optional<EquipeVue> monEquipe(Long joueurId) {
        Joueur j = acces.joueur(joueurId);
        if (j.getEquipe() == null) return Optional.empty();
        Equipe e = j.getEquipe();
        return Optional.of(new EquipeVue(e.getId(), e.getNom(), e.getClub().getNom(), e.getLogo(),
                e.getAncienLogoPartage() != null, effectif(e.getId())));
    }

    public List<JoueurVue> effectif(Long equipeId) {
        return joueurRepository.findByEquipeId(equipeId).stream()
                .sorted(Comparator.comparing(Joueur::getNom))
                .map(x -> new JoueurVue(x.getId(), x.getNom(), x.getPrenom(), x.getPoste(),
                        x.getNumeroMaillot(), x.isEstCapitaine())).toList();
    }

    public List<MatchVue> matchsDeMonEquipe(Long joueurId) {
        Joueur j = acces.joueur(joueurId);
        if (j.getEquipe() == null) return List.of();
        return equipeMatchRepository.findByEquipeId(j.getEquipe().getId()).stream()
                .map(EquipeMatch::getMatch).sorted(Comparator.comparing(Match::getDateHeure, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingInt(Match::getNumero))
                .map(this::vue).toList();
    }

    // ---- conversions ----

    private TournoiResume resume(Tournoi t) {
        return new TournoiResume(t.getId(), t.getNom(), t.getSport(), t.getDateDebut(), t.getDateFin(), t.getStatut());
    }

    private String nomAuRang(Long tournoiId, int rang) {
        return inscriptionRepository.findFirstByTournoiIdAndRangFinal(tournoiId, rang)
                .map(i -> i.getEquipe().getNom()).orElse(null);
    }

    private MatchVue vue(Match m) {
        EquipeMatch a = m.getEquipeMatchs().get(0);
        EquipeMatch b = m.getEquipeMatchs().get(1);
        boolean joue = m.getStatut() == StatutMatch.TERMINE || m.getStatut() == StatutMatch.FORFAIT;
        return new MatchVue(m.getId(), m.getNumero(), m.getPhase(),
                m.getGroupe() == null ? null : m.getGroupe().getNom(),
                m.getDateHeure(), m.getStatut(), nom(a), nom(b),
                joue ? a.getScore() : null, joue ? b.getScore() : null,
                a.getTirsAuBut(), b.getTirsAuBut());
    }

    private String nom(EquipeMatch em) {
        return em.getEquipe() == null ? "À déterminer" : em.getEquipe().getNom();
    }

    private List<LigneFeuille> feuille(EquipeMatch em) {
        return em.getJoueurs().stream()
                .sorted(Comparator.comparing(JoueurMatch::isEstTitulaire).reversed().thenComparing(jm -> jm.getJoueur().getId()))
                .map(jm -> new LigneFeuille(jm.getJoueur().getId(), jm.getJoueur().getPrenom() + " " + jm.getJoueur().getNom(),
                        jm.getJoueur().getNumeroMaillot(), jm.isEstTitulaire(), jm.getButs(),
                        jm.getCartonsJaunes(), jm.isCartonRouge(), jm.getMinuteEntree(), jm.getMinuteSortie()))
                .toList();
    }
}
