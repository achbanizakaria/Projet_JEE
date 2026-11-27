package com.example.demo.web;

import com.example.demo.dto.Vues.EquipeVue;
import com.example.demo.service.CompteUserDetails;
import com.example.demo.service.ConsultationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class AccueilController {

    private final ConsultationService consultationService;

    /** Accueil : l'organisateur voit ses liens ; le joueur voit sa situation (équipe, capitaine) lue en base. */
    @GetMapping("/")
    public String accueil(@AuthenticationPrincipal CompteUserDetails utilisateur, Model model) {
        model.addAttribute("nom", utilisateur.getNomComplet());
        model.addAttribute("organisateur", utilisateur.isOrganisateur());
        if (!utilisateur.isOrganisateur()) {
            Optional<EquipeVue> equipe = consultationService.monEquipe(utilisateur.getId());
            model.addAttribute("equipe", equipe.orElse(null));
            model.addAttribute("capitaine", OutilsWeb.estCapitaine(equipe.orElse(null), utilisateur.getId()));
        }
        return "accueil";
    }
}