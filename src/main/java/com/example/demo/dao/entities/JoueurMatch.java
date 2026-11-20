package com.example.demo.dao.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "joueur_match",
        uniqueConstraints = @UniqueConstraint(columnNames = {"equipe_match_id", "joueur_id"}))
@Getter @Setter @NoArgsConstructor
public class JoueurMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "est_titulaire", nullable = false)
    private boolean estTitulaire = true;

    @Column(name = "minute_entree")
    private Integer minuteEntree;

    @Column(name = "minute_sortie")
    private Integer minuteSortie;

    private int buts;

    @Column(name = "cartons_jaunes")
    private int cartonsJaunes;         // 2 = rouge indirect

    @Column(name = "carton_rouge")
    private boolean cartonRouge;       // rouge direct

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipe_match_id")
    private EquipeMatch equipeMatch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "joueur_id")
    private Joueur joueur;
}