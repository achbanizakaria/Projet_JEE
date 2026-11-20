package com.example.demo.dao.repositories;

import com.example.demo.dao.entities.Match;
import com.example.demo.dao.entities.PhaseMatch;
import com.example.demo.dao.entities.StatutMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findByTournoiIdOrderByNumeroAsc(Long tournoiId);
    List<Match> findByTournoiIdOrderByDateHeureAsc(Long tournoiId);
    List<Match> findByGroupeId(Long groupeId);
    List<Match> findByTournoiIdAndPhase(Long tournoiId, PhaseMatch phase);
    long countByTournoiIdAndPhaseAndStatutNotIn(Long tournoiId, PhaseMatch phase,
                                                java.util.Collection<StatutMatch> statuts);
}