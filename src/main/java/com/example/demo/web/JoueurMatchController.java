package com.example.demo.web;

import com.example.demo.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/joueur/matchs")
@RequiredArgsConstructor
public class JoueurMatchController {
    private final MatchService matchs;
    private final ConsultationService consultation;

    @GetMapping
    public String liste(@AuthenticationPrincipal CompteUserDetails joueur, Model model) {
        model.addAttribute("matchs", consultation.matchsDeMonEquipe(joueur.getId()));
        return "joueur/matchs";
    }

    @PostMapping("/{id}/forfait")
    public String forfait(@AuthenticationPrincipal CompteUserDetails joueur, @PathVariable("id") Long id,
                          @RequestParam("motif") String motif, RedirectAttributes flash) {
        matchs.declarerForfait(joueur.getId(), id, motif);
        flash.addFlashAttribute("succes", "Forfait de votre équipe enregistré");
        return "redirect:/consultation/matchs/" + id;
    }

}
