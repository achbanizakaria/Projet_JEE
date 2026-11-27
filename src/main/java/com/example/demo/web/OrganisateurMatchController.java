package com.example.demo.web;

import com.example.demo.dto.JoueurStatForm;
import com.example.demo.dto.ResultatForm;
import com.example.demo.dto.Vues.DetailMatch;
import com.example.demo.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/organisateur/matchs")
@RequiredArgsConstructor
public class OrganisateurMatchController {
    private final MatchService matchs;
    private final ConsultationService consultation;

    @GetMapping("/{id}/resultat")
    public String resultat(@AuthenticationPrincipal CompteUserDetails responsable, @PathVariable("id") Long id, Model model) {
        ResultatForm form = matchs.formulaireResultat(responsable.getId(), id);
        model.addAttribute("resultatForm", form);
        return page(id, form, model);
    }

    @PostMapping("/{id}/resultat")
    public String enregistrer(@AuthenticationPrincipal CompteUserDetails responsable, @PathVariable("id") Long id,
                              @Valid @ModelAttribute("resultatForm") ResultatForm form, BindingResult result,
                              Model model, RedirectAttributes flash) {
        matchs.formulaireResultat(responsable.getId(), id);
        if (!result.hasErrors()) {
            retirerRemplacantVide(form.getJoueursEquipe1());
            retirerRemplacantVide(form.getJoueursEquipe2());
            try {
                matchs.enregistrerResultat(responsable.getId(), id, form);
                flash.addFlashAttribute("succes", "Résultat et feuilles de match enregistrés");
                return "redirect:/consultation/matchs/" + id;
            } catch (RegleMetierException e) {
                result.reject("resultat.invalide", e.getMessage());
            }
        }
        return page(id, form, model);
    }

    @PostMapping("/{id}/forfait/{equipeId}")
    public String forfait(@AuthenticationPrincipal CompteUserDetails responsable, @PathVariable("id") Long id,
                          @PathVariable("equipeId") Long equipeId, @RequestParam("motif") String motif,
                          RedirectAttributes flash) {
        matchs.declarerForfaitParOrganisateur(responsable.getId(), id, equipeId, motif);
        flash.addFlashAttribute("succes", "Forfait enregistré");
        return "redirect:/consultation/matchs/" + id;
    }

    private String page(Long id, ResultatForm form, Model model) {
        DetailMatch detail = consultation.detailMatch(id);
        preparerLignes(form.getJoueursEquipe1(), detail.equipe1().size());
        preparerLignes(form.getJoueursEquipe2(), detail.equipe2().size());
        model.addAttribute("detail", detail);
        model.addAttribute("effectif1", consultation.effectif(detail.equipe1Id()));
        model.addAttribute("effectif2", consultation.effectif(detail.equipe2Id()));
        return "organisateur/resultat";
    }

    private void preparerLignes(List<JoueurStatForm> lignes, int tailleComposition) {
        int taille = tailleComposition == 0 ? Regles.NB_TITULAIRES + Regles.NB_REMPLACANTS_MAX : tailleComposition;
        while (lignes.size() < taille) {
            JoueurStatForm ligne = new JoueurStatForm();
            ligne.setEstTitulaire(lignes.size() < Regles.NB_TITULAIRES);
            lignes.add(ligne);
        }
    }

    private void retirerRemplacantVide(List<JoueurStatForm> lignes) {
        if (lignes.size() != Regles.NB_TITULAIRES + 1) return;
        JoueurStatForm r = lignes.getLast();
        if (r != null && r.getJoueurId() == null && !r.isEstTitulaire() && r.getButs() == 0 && r.getCartonsJaunes() == 0
                && !r.isCartonRouge() && r.getMinuteEntree() == null && r.getMinuteSortie() == null) {
            lignes.removeLast();
        }
    }
}
