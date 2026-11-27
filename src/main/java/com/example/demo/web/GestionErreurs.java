package com.example.demo.web;

import com.example.demo.service.AccesRefuseException;
import com.example.demo.service.CompteUserDetails;
import com.example.demo.service.RegleMetierException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;
import java.net.URISyntaxException;

/** Socle commun à tous les contrôleurs : utilisateur disponible dans les pages et gestion des erreurs métier. */
@ControllerAdvice
public class GestionErreurs {

    /** Rend l'utilisateur connecté (ou null) disponible dans toutes les pages, pour la barre de navigation. */
    @ModelAttribute("utilisateur")
    public CompteUserDetails utilisateur(@AuthenticationPrincipal CompteUserDetails utilisateur) {
        return utilisateur;
    }

    /**
     * Règle métier violée pendant une action (bouton) : on revient à la page précédente
     * avec le message d'erreur. Les formulaires gèrent eux-mêmes leurs erreurs de saisie.
     */
    @ExceptionHandler(RegleMetierException.class)
    public String regleMetier(RegleMetierException e, HttpServletRequest request, RedirectAttributes flash) {
        flash.addFlashAttribute("erreur", e.getMessage());
        return "redirect:" + cheminPrecedent(request.getHeader("Referer"), request.getRequestURI());
    }

    /** Droit manquant : page 403 avec le motif. */
    @ExceptionHandler(AccesRefuseException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String accesRefuse(AccesRefuseException e, Model model) {
        model.addAttribute("message", e.getMessage());
        return "error/403";
    }

    /**
     * Chemin local (sans serveur) de la page d'origine, à partir de l'en-tête Referer.
     * Retourne "/" si l'en-tête est absent, illisible, ou désigne la requête en cours (évite une boucle).
     */
    static String cheminPrecedent(String referer, String requeteEnCours) {
        if (referer == null || referer.isBlank()) return "/";
        try {
            URI uri = new URI(referer);
            String chemin = uri.getRawPath();
            if (chemin == null || !chemin.startsWith("/") || chemin.startsWith("//")) return "/";
            if (chemin.equals(requeteEnCours)) return "/";
            return uri.getRawQuery() == null ? chemin : chemin + "?" + uri.getRawQuery();
        } catch (URISyntaxException ex) {
            return "/";
        }
    }
}