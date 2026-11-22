package com.example.demo.dto;

import com.example.demo.dao.entities.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Objets d'affichage en lecture seule : aucune entité n'est exposée aux vues. */
public final class Vues {
    private Vues() {}

    public record ClubVue(Long id, String nom, String logo) {}

    public record TournoiResume(Long id, String nom, String sport, LocalDate dateDebut,
                                LocalDate dateFin, StatutTournoi statut) {}

    public record TournoiTermine(Long id, String nom, String sport, LocalDate dateDebut,
                                 LocalDate dateFin, String champion, String finaliste) {}

    public record MatchVue(Long id, int numero, PhaseMatch phase, String groupe,
                           LocalDateTime dateHeure, StatutMatch statut,
                           String equipe1, String equipe2,
                           Integer score1, Integer score2, Integer tirs1, Integer tirs2) {}

    public record LigneClassement(int rang, String equipe, int joues, int gagnes, int nuls,
                                  int perdus, int butsPour, int butsContre, int difference,
                                  int points, int fairPlay) {}

    public record GroupeVue(String nom, List<LigneClassement> lignes) {}

    public record LigneFeuille(String joueur, Integer numero, boolean titulaire, int buts,
                               int cartonsJaunes, boolean cartonRouge,
                               Integer minuteEntree, Integer minuteSortie) {}

    public record DetailMatch(MatchVue match, List<LigneFeuille> equipe1, List<LigneFeuille> equipe2) {}

    public record JoueurVue(Long id, String nom, String prenom, String poste,
                            Integer numeroMaillot, boolean capitaine) {}

    public record EquipeVue(Long id, String nom, String club, List<JoueurVue> joueurs) {}

    /** Vue de l'organisateur : une demande d'inscription et l'état de l'effectif. */
    public record InscriptionVue(Long id, String equipe, String club, long effectif,
                                 long capitaines, LocalDate dateInscription,
                                 StatutInscription statut, String motifRefus) {}

    /** Vue du capitaine : l'état de ses propres demandes. */
    public record MonInscription(Long id, Long tournoiId, String tournoi,
                                 StatutInscription statut, String motifRefus) {}
}