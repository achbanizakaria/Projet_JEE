package com.example.demo.web;

import com.example.demo.dto.ClubForm;
import com.example.demo.dto.Vues.ClubVue;
import com.example.demo.service.ClubService;
import com.example.demo.service.RegleMetierException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** UC09 : gestion des clubs (réservée à l'organisateur par SecurityConfig). */
@Controller
@RequestMapping("/organisateur/clubs")
@RequiredArgsConstructor
public class OrganisateurClubController {

    private final ClubService clubService;

    @GetMapping
    public String liste(Model model) {
        model.addAttribute("clubs", clubService.lister());
        model.addAttribute("clubForm", new ClubForm());
        return "organisateur/clubs";
    }

    @PostMapping
    public String creer(@Valid @ModelAttribute("clubForm") ClubForm form, BindingResult result,
                        Model model, RedirectAttributes flash) {
        if (!result.hasErrors()) {
            try {
                ClubVue club = clubService.creer(form);
                flash.addFlashAttribute("succes", "Club « " + club.nom() + " » créé");
                return "redirect:/organisateur/clubs";
            } catch (RegleMetierException e) {
                result.rejectValue("nom", "club.nom.utilise", e.getMessage());
            }
        }
        model.addAttribute("clubs", clubService.lister());
        return "organisateur/clubs";
    }

    @GetMapping("/{id}/modifier")
    public String modifier(@PathVariable("id") Long id, Model model) {
        ClubVue club = clubService.trouver(id);
        ClubForm form = new ClubForm();
        form.setNom(club.nom());
        form.setLogo(club.logo());
        model.addAttribute("clubForm", form);
        model.addAttribute("clubId", id);
        return "organisateur/club-modifier";
    }

    @PostMapping("/{id}/modifier")
    public String enregistrer(@PathVariable("id") Long id,
                              @Valid @ModelAttribute("clubForm") ClubForm form, BindingResult result,
                              Model model, RedirectAttributes flash) {
        clubService.trouver(id);                       // club inexistant : message général, pas d'erreur de champ
        if (!result.hasErrors()) {
            try {
                clubService.modifier(id, form);
                flash.addFlashAttribute("succes", "Club modifié");
                return "redirect:/organisateur/clubs";
            } catch (RegleMetierException e) {
                result.rejectValue("nom", "club.nom.utilise", e.getMessage());
            }
        }
        model.addAttribute("clubId", id);
        return "organisateur/club-modifier";
    }

    /** Un club qui possède des équipes ne peut pas être supprimé : le message revient par GestionErreurs. */
    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable("id") Long id, RedirectAttributes flash) {
        clubService.supprimer(id);
        flash.addFlashAttribute("succes", "Club supprimé");
        return "redirect:/organisateur/clubs";
    }
}