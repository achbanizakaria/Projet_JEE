package com.example.demo.service;

import com.example.demo.dao.entities.Match;
import com.example.demo.dao.entities.Tournoi;
import com.example.demo.dao.repositories.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PlanificationStade {

    private final MatchRepository matchRepository;

    /**
     * Créneaux libres du tournoi, dans l'ordre chronologique, strictement après « apres » (si non nul).
     * Chaque réservation comprend la durée et le repos de son propre tournoi.
     */
    public List<LocalDateTime> creneauxLibres(Tournoi t, LocalDateTime apres) {
        int duree = t.getDureeMatchMinutes();
        long pas = (long) duree + t.getReposMinutes();
        LocalDateTime premier = t.getDateDebut().atTime(t.getHeureOuverture());
        LocalDateTime limiteRecherche = t.getDateFin().atTime(t.getHeureFermeture()).plusMinutes(t.getReposMinutes());
        List<Occupation> occupes = matchRepository.findByDateHeureBefore(limiteRecherche).stream()
                .map(Occupation::duMatch).filter(o -> o.fin().isAfter(premier)).toList();
        List<LocalDateTime> libres = new ArrayList<>();
        LocalDateTime libreDepuis = premier;
        for (LocalDate jour = t.getDateDebut(); !jour.isAfter(t.getDateFin()); jour = jour.plusDays(1)) {
            LocalDateTime debut = jour.atTime(t.getHeureOuverture());
            // Le repos du dernier créneau peut se terminer après l'ouverture du lendemain.
            if (debut.isBefore(libreDepuis)) debut = libreDepuis;
            LocalDateTime limite = jour.atTime(t.getHeureFermeture());
            while (!debut.plusMinutes(duree).isAfter(limite)) {
                Occupation candidat = new Occupation(debut, debut.plusMinutes(pas));
                if ((apres == null || debut.isAfter(apres)) && occupes.stream().noneMatch(candidat::chevauche)) {
                    libres.add(debut);
                    libreDepuis = candidat.fin();
                }
                debut = debut.plusMinutes(pas);
            }
        }
        return libres;
    }

    private record Occupation(LocalDateTime debut, LocalDateTime fin) {
        static Occupation duMatch(Match m) {
            Tournoi t = m.getTournoi();
            return new Occupation(m.getDateHeure(), m.getDateHeure().plusMinutes((long) t.getDureeMatchMinutes() + t.getReposMinutes()));
        }

        boolean chevauche(Occupation autre) {
            return debut.isBefore(autre.fin) && autre.debut.isBefore(fin);
        }
    }
}
