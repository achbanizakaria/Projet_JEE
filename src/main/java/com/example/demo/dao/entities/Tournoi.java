package com.example.demo.dao.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter @Setter @NoArgsConstructor
public class Tournoi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,unique=true, length = 120)
    private String nom;

    @Column(nullable = false, length = 50)
    private String sport;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private StatutTournoi statut = StatutTournoi.INSCRIPTION_OUVERTE;

    @Column(name = "nb_groupes", nullable = false)
    private int nbGroupes;

    @Column(name = "nb_qualifies_par_groupe", nullable = false)
    private int nbQualifiesParGroupe;

    // Paramètres de planification
    @Column(name = "duree_match_minutes", nullable = false)
    private int dureeMatchMinutes = 30;

    @Column(name = "heure_ouverture", nullable = false)
    private LocalTime heureOuverture = LocalTime.of(12, 30);

    @Column(name = "heure_fermeture", nullable = false)
    private LocalTime heureFermeture = LocalTime.of(14, 30);

    @Column(name = "repos_minutes", nullable = false)
    private int reposMinutes = 0;

    @Column(name = "score_forfait", nullable = false)
    private int scoreForfait = 3;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisateur_id")
    private Organisateur organisateur;

    @OneToMany(mappedBy = "tournoi", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Inscription> inscriptions = new ArrayList<>();

    @OneToMany(mappedBy = "tournoi", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Groupe> groupes = new ArrayList<>();

    @OneToMany(mappedBy = "tournoi", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Match> matchs = new ArrayList<>();
}