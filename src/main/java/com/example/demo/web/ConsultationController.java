package com.example.demo.web;

import com.example.demo.service.ConsultationService;
import com.example.demo.service.CompteUserDetails;
import com.example.demo.service.MatchService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * UC02, UC03, UC04 : consultation du calendrier, des classements, du tableau et de l'historique.
 * Lecture seule, ouverte à tout utilisateur connecté (organisateur compris).
 */
@Controller
@RequestMapping("/consultation")
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationService consultationService;
    private final MatchService matchService;

    /** Tournois en cours (le plus récent en premier) et historique des tournois terminés. */
    @GetMapping("/tournois")
    public String tournois(Model model) {
        model.addAttribute("enCours", consultationService.tournoisEnCours());
        model.addAttribute("termines", consultationService.historique());
        return "consultation/tournois";
    }

    @GetMapping("/tournois/{id}")
    public String tournoi(@PathVariable("id") Long id, Model model) {
        model.addAttribute("tournoi", consultationService.tournoi(id));
        model.addAttribute("groupes", consultationService.classements(id));
        model.addAttribute("matchs", consultationService.calendrier(id));
        model.addAttribute("tableau", consultationService.tableauEliminatoire(id));
        return "consultation/tournoi";
    }

    /** Un match : score, composition, buteurs et cartons. */
    @GetMapping("/matchs/{id}")
    public String match(@PathVariable("id") Long id, @AuthenticationPrincipal CompteUserDetails utilisateur, Model model) {
        model.addAttribute("detail", consultationService.detailMatch(id));
        model.addAttribute("actions", matchService.actions(utilisateur.getId(), utilisateur.isOrganisateur(), id));
        return "consultation/match";
    }
}
