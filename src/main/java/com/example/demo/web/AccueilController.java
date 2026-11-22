package com.example.demo.web;

import com.example.demo.service.CompteUserDetails;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccueilController {

    /** Page provisoire : affiche l'utilisateur et ses rôles. Les menus par rôle viendront avec les contrôleurs. */
    @GetMapping("/")
    public String accueil(@AuthenticationPrincipal CompteUserDetails utilisateur, Model model) {
        model.addAttribute("nom", utilisateur.getNomComplet());
        model.addAttribute("roles", utilisateur.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList());
        return "accueil";
    }
}