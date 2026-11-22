package com.example.demo.dao.repositories;

import com.example.demo.dao.entities.Match;
import com.example.demo.dao.entities.PhaseMatch;
import com.example.demo.dao.entities.StatutMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findByTournoiIdOrderByNumeroAsc(Long tournoiId);
    List<Match> findByGroupeId(Long groupeId);
    List<Match> findByTournoiIdAndPhase(Long tournoiId, PhaseMatch phase);

    /** Matchs de poule qui ne sont ni terminés ni attribués par forfait (UC13). */
    long countByTournoiIdAndPhaseAndStatutNotIn(Long tournoiId, PhaseMatch phase,
                                                Collection<StatutMatch> statuts);

    long countByTournoiIdAndStatutIn(Long tournoiId, Collection<StatutMatch> statuts);

    /** Coups d'envoi existants dans un intervalle (l'unique stade est partagé entre tournois). */
    List<Match> findByDateHeureBetween(LocalDateTime debut, LocalDateTime fin);

    /** Matchs d'une équipe dans un tournoi, par numéro. */
    List<Match> findDistinctByTournoiIdAndEquipeMatchsEquipeIdOrderByNumeroAsc(Long tournoiId, Long equipeId);
}