package com.example.demo.web;

import com.example.demo.dto.Vues.EquipeVue;
import com.example.demo.dto.Vues.MonInscription;
import com.example.demo.dto.Vues.TournoiResume;
import com.example.demo.service.CompteUserDetails;
import com.example.demo.service.ConsultationService;
import com.example.demo.service.InscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** UC06 : tournois ouverts aux inscriptions et demandes de l'équipe (réservées à son capitaine). */
@Controller
@RequestMapping("/joueur/tournois")
@RequiredArgsConstructor
public class JoueurTournoiController {

    private final ConsultationService consultationService;
    private final InscriptionService inscriptionService;

    @GetMapping
    public String liste(@AuthenticationPrincipal CompteUserDetails joueur, Model model) {
        Optional<EquipeVue> equipe = consultationService.monEquipe(joueur.getId());
        boolean capitaine = OutilsWeb.estCapitaine(equipe.orElse(null), joueur.getId());

        Map<Long, MonInscription> demandes = new HashMap<>();
        if (capitaine) {
            for (MonInscription i : inscriptionService.mesInscriptions(joueur.getId())) {
                demandes.put(i.tournoiId(), i);
            }
        }
        List<TournoiResume> ouverts = consultationService.tournoisOuverts();
        Set<Long> idsOuverts = ouverts.stream().map(TournoiResume::id).collect(Collectors.toSet());
        model.addAttribute("autresInscriptions", demandes.values().stream()
                .filter(i -> !idsOuverts.contains(i.tournoiId())).sorted(Comparator.comparing(MonInscription::tournoi)).toList());
        model.addAttribute("tournois", ouverts);
        model.addAttribute("aEquipe", equipe.isPresent());
        model.addAttribute("capitaine", capitaine);
        model.addAttribute("demandes", demandes);
        return "joueur/tournois";
    }

    @PostMapping("/{id}/inscrire")
    public String inscrire(@AuthenticationPrincipal CompteUserDetails capitaine,
                           @PathVariable("id") Long id, RedirectAttributes flash) {
        inscriptionService.demander(capitaine.getId(), id);
        flash.addFlashAttribute("succes", "Demande d'inscription envoyée : elle sera traitée par l'organisateur");
        return "redirect:/joueur/tournois";
    }
}
