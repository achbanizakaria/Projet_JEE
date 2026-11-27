package com.example.demo.service;

import com.example.demo.dao.entities.Match;
import com.example.demo.dao.entities.Tournoi;
import com.example.demo.dao.repositories.MatchRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlanificationStadeTests {
    @Test
    void leReposDuDernierMatchSeProlongeSurLaJourneeSuivante() {
        LocalDate mercredi = LocalDate.of(2026, 11, 25);
        Tournoi tournoi = new Tournoi();
        tournoi.setDateDebut(mercredi);
        tournoi.setDateFin(mercredi.plusDays(2));
        tournoi.setDureeMatchMinutes(30);
        tournoi.setReposMinutes(24 * 60);
        MatchRepository depot = mock(MatchRepository.class);
        when(depot.findByDateHeureBefore(any())).thenReturn(List.of());
        assertThat(new PlanificationStade(depot).creneauxLibres(tournoi, null))
                .containsExactly(mercredi.atTime(12, 30), mercredi.plusDays(1).atTime(13, 0), mercredi.plusDays(2).atTime(13, 30));
    }

    @Test
    void deuxTournoisPartagentLeStadeAvecLeursPropresDureesEtRepos() {
        LocalDate jour = LocalDate.of(2026, 11, 25);
        Tournoi longTournoi = new Tournoi();
        longTournoi.setDureeMatchMinutes(60);
        longTournoi.setReposMinutes(15);
        Match reserve = new Match();
        reserve.setTournoi(longTournoi);
        reserve.setDateHeure(jour.atTime(12, 30));
        MatchRepository depot = mock(MatchRepository.class);
        when(depot.findByDateHeureBefore(any())).thenReturn(List.of(reserve));
        Tournoi court = new Tournoi();
        court.setDateDebut(jour);
        court.setDateFin(jour);
        court.setHeureOuverture(LocalTime.of(11, 45));
        court.setHeureFermeture(LocalTime.of(14, 30));
        court.setDureeMatchMinutes(30);
        court.setReposMinutes(15);
        assertThat(new PlanificationStade(depot).creneauxLibres(court, null))
                .containsExactly(jour.atTime(11, 45), jour.atTime(14, 0));
    }
}
