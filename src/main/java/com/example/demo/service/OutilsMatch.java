package com.example.demo.service;

import com.example.demo.dao.entities.*;

import java.util.Optional;

public final class OutilsMatch {
    private OutilsMatch() {}

    public static EquipeMatch participation(Match match, int ordre, Equipe equipe) {
        EquipeMatch em = new EquipeMatch();
        em.setMatch(match);
        em.setOrdre(ordre);
        em.setEquipe(equipe);
        return em;
    }

    public static Optional<EquipeMatch> deEquipe(Match m, Long equipeId) {
        return m.getEquipeMatchs().stream()
                .filter(em -> em.getEquipe() != null && em.getEquipe().getId().equals(equipeId))
                .findFirst();
    }

    public static EquipeMatch adversaire(Match m, EquipeMatch em) {
        return m.getEquipeMatchs().stream().filter(x -> x != em).findFirst()
                .orElseThrow(() -> new IllegalStateException("Match sans adversaire"));
    }

    /** Vainqueur : forfait de l'adversaire, sinon score, sinon tirs au but. Vide si indéterminé. */
    public static Optional<EquipeMatch> vainqueur(Match m) {
        if (m.getEquipeMatchs().size() != 2) return Optional.empty();
        EquipeMatch a = m.getEquipeMatchs().get(0);
        EquipeMatch b = m.getEquipeMatchs().get(1);
        if (m.getStatut() == StatutMatch.FORFAIT) {
            if (a.isForfait() == b.isForfait()) return Optional.empty();
            return Optional.of(a.isForfait() ? b : a);
        }
        if (m.getStatut() != StatutMatch.TERMINE) return Optional.empty();
        if (a.getScore() != b.getScore()) return Optional.of(a.getScore() > b.getScore() ? a : b);
        if (a.getTirsAuBut() != null && b.getTirsAuBut() != null
                && !a.getTirsAuBut().equals(b.getTirsAuBut())) {
            return Optional.of(a.getTirsAuBut() > b.getTirsAuBut() ? a : b);
        }
        return Optional.empty();
    }

    public static Optional<EquipeMatch> perdant(Match m) {
        return vainqueur(m).map(v -> adversaire(m, v));
    }
}