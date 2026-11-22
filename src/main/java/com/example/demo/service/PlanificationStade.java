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
     * Un créneau est exclu s'il chevauche un match existant (de n'importe quel tournoi),
     * en supposant la même durée de match.
     */
    public List<LocalDateTime> creneauxLibres(Tournoi t, LocalDateTime apres) {
        int duree = t.getDureeMatchMinutes();
        int pas = duree + t.getReposMinutes();
        List<LocalDateTime> occupes = matchRepository.findByDateHeureBetween(
                        t.getDateDebut().atStartOfDay().minusMinutes(duree),
                        t.getDateFin().plusDays(1).atStartOfDay())
                .stream().map(Match::getDateHeure).toList();
        List<LocalDateTime> libres = new ArrayList<>();
        for (LocalDate jour = t.getDateDebut(); !jour.isAfter(t.getDateFin()); jour = jour.plusDays(1)) {
            LocalDateTime debut = jour.atTime(t.getHeureOuverture());
            LocalDateTime limite = jour.atTime(t.getHeureFermeture());
            while (!debut.plusMinutes(duree).isAfter(limite)) {
                if ((apres == null || debut.isAfter(apres)) && !chevauche(debut, duree, occupes)) {
                    libres.add(debut);
                }
                debut = debut.plusMinutes(pas);
            }
        }
        return libres;
    }

    private boolean chevauche(LocalDateTime debut, int duree, List<LocalDateTime> occupes) {
        LocalDateTime fin = debut.plusMinutes(duree);
        return occupes.stream().anyMatch(o -> o.isBefore(fin) && o.plusMinutes(duree).isAfter(debut));
    }
}