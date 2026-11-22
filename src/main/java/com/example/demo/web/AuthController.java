package com.example.demo.web;

import com.example.demo.dto.InscriptionForm;
import com.example.demo.dto.MotDePasseForm;
import com.example.demo.service.CompteService;
import com.example.demo.service.CompteUserDetails;
import com.example.demo.service.RegleMetierException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final CompteService compteService;

    @GetMapping("/connexion")
    public String connexion() {
        return "connexion";
    }

    @GetMapping("/inscription")
    public String inscription(Model model) {
        model.addAttribute("inscriptionForm", new InscriptionForm());
        return "inscription";
    }

    /** Inscription publique : crée un joueur sans équipe. */
    @PostMapping("/inscription")
    public String inscrire(@Valid @ModelAttribute("inscriptionForm") InscriptionForm form,
                           BindingResult result) {
        if (!result.hasFieldErrors("motDePasse") && !result.hasFieldErrors("confirmationMotDePasse")
                && !form.getMotDePasse().equals(form.getConfirmationMotDePasse())) {
            result.rejectValue("confirmationMotDePasse", "confirmation.differente",
                    "Les deux mots de passe ne correspondent pas");
        }
        if (result.hasErrors()) {
            return "inscription";
        }
        try {
            compteService.inscrireJoueur(form.versCompte());
        } catch (RegleMetierException e) {
            result.rejectValue("email", "email.utilise", e.getMessage());
            return "inscription";
        }
        return "redirect:/connexion?inscrit";
    }

    @GetMapping("/mot-de-passe")
    public String motDePasse(Model model) {
        model.addAttribute("motDePasseForm", new MotDePasseForm());
        return "mot-de-passe";
    }

    /** Après le changement, l'utilisateur est déconnecté et se reconnecte avec le nouveau mot de passe. */
    @PostMapping("/mot-de-passe")
    public String changerMotDePasse(@AuthenticationPrincipal CompteUserDetails utilisateur,
                                    @Valid @ModelAttribute("motDePasseForm") MotDePasseForm form,
                                    BindingResult result,
                                    Authentication authentication,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        if (!result.hasFieldErrors("nouveauMotDePasse") && !result.hasFieldErrors("confirmationNouveauMotDePasse")
                && !form.getNouveauMotDePasse().equals(form.getConfirmationNouveauMotDePasse())) {
            result.rejectValue("confirmationNouveauMotDePasse", "confirmation.differente",
                    "Les deux mots de passe ne correspondent pas");
        }
        if (result.hasErrors()) {
            return "mot-de-passe";
        }
        try {
            compteService.changerMotDePasse(utilisateur.getId(), form);
        } catch (RegleMetierException e) {
            result.reject("mot-de-passe.invalide", e.getMessage());
            return "mot-de-passe";
        }
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/connexion?motDePasseChange";
    }
}