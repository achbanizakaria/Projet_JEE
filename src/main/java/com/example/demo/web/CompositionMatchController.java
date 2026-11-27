package com.example.demo.web;

import com.example.demo.dto.CompositionForm;
import com.example.demo.dto.Vues.DetailMatch;
import com.example.demo.dto.Vues.EquipeVue;
import com.example.demo.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class CompositionMatchController {
    private static final String CAPITAINE = "/joueur/matchs/{id}/composition";
    private static final String ORGANISATEUR = "/organisateur/matchs/{id}/equipes/{equipeId}/composition";
    private final MatchService matchs;
    private final ConsultationService consultation;

    @GetMapping({CAPITAINE, ORGANISATEUR})
    public String composition(@AuthenticationPrincipal CompteUserDetails acteur, @PathVariable("id") Long id,
                              @PathVariable(name = "equipeId", required = false) Long equipeId, Model model) {
        CompositionForm form = equipeId == null ? matchs.formulaireComposition(acteur.getId(), id)
                : matchs.compositionOrganisateur(acteur.getId(), id, equipeId);
        model.addAttribute("compositionForm", form);
        return page(acteur, id, equipeId, model);
    }

    @PostMapping({CAPITAINE, ORGANISATEUR})
    public String enregistrer(@AuthenticationPrincipal CompteUserDetails acteur, @PathVariable("id") Long id,
                              @PathVariable(name = "equipeId", required = false) Long equipeId,
                              @ModelAttribute("compositionForm") CompositionForm form, BindingResult result,
                              Model model, RedirectAttributes flash) {
        if (!result.hasErrors()) {
            try {
                if (equipeId == null) matchs.definirComposition(acteur.getId(), id, form);
                else matchs.preparerComposition(acteur.getId(), id, equipeId, form);
                flash.addFlashAttribute("succes", "Composition enregistrée");
                return "redirect:/consultation/matchs/" + id;
            } catch (RegleMetierException e) {
                result.reject("composition.invalide", e.getMessage());
            }
        }
        // Vérifier aussi les droits lors du réaffichage d'une saisie invalide.
        if (equipeId == null) matchs.formulaireComposition(acteur.getId(), id);
        else matchs.compositionOrganisateur(acteur.getId(), id, equipeId);
        return page(acteur, id, equipeId, model);
    }

    private String page(CompteUserDetails acteur, Long id, Long equipeId, Model model) {
        DetailMatch detail = consultation.detailMatch(id);
        model.addAttribute("detail", detail);
        model.addAttribute("equipeCible", equipeId);
        if (equipeId == null) {
            EquipeVue equipe = consultation.monEquipe(acteur.getId()).orElseThrow();
            model.addAttribute("nomEquipe", equipe.nom());
            model.addAttribute("effectif", equipe.joueurs());
        } else {
            model.addAttribute("nomEquipe", equipeId.equals(detail.equipe1Id()) ? detail.match().equipe1() : detail.match().equipe2());
            model.addAttribute("effectif", consultation.effectif(equipeId));
        }
        return "matchs/composition";
    }
}
