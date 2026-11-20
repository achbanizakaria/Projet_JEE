package com.example.demo.dao.enteties;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "classement_groupe",
        uniqueConstraints = @UniqueConstraint(columnNames = {"groupe_id", "equipe_id"}))
@Getter @Setter @NoArgsConstructor
public class ClassementGroupe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int points;
    private int joues;
    private int gagnes;
    private int nuls;
    private int perdus;

    @Column(name = "buts_pour")
    private int butsPour;

    @Column(name = "buts_contre")
    private int butsContre;

    private int difference;

    @Column(name = "fair_play")
    private int fairPlay;          // ≤ 0 : plus proche de 0 = meilleur

    @Column(name = "tirage_sort")
    private Integer tirageSort;    // rang imposé par l'organisateur en cas d'égalité parfaite

    private Integer rang;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "groupe_id")
    private Groupe groupe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipe_id")
    private Equipe equipe;
}
