package com.example.demo.web;

import com.example.demo.dto.Vues.EquipeVue;

/** Petits outils communs aux contrôleurs. */
final class OutilsWeb {
    private OutilsWeb() {}

    /**
     * Vrai si le joueur est le capitaine de cette équipe, d'après les données lues en base à l'instant.
     * On n'utilise pas le rôle conservé en session : il ne suit pas un changement de capitaine.
     */
    static boolean estCapitaine(EquipeVue equipe, Long joueurId) {
        return equipe != null
                && equipe.joueurs().stream().anyMatch(j -> j.id().equals(joueurId) && j.capitaine());
    }
}