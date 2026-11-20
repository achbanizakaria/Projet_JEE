package com.example.demo.dao.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "equipe_match", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"match_id", "ordre"}),
        @UniqueConstraint(columnNames = {"match_id", "equipe_id"})
})
@Getter @Setter @NoArgsConstructor
public class EquipeMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int ordre;                 // 1 ou 2

    @Column(nullable = false)
    private int score;

    @Column(name = "tirs_au_but")
    private Integer tirsAuBut;

    @Column(nullable = false)
    private boolean forfait;

    @Column(name = "motif_forfait")
    private String motifForfait;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id")
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipe_id")    // null tant que l'équipe est inconnue (arbre éliminatoire)
    private Equipe equipe;

    @OneToMany(mappedBy = "equipeMatch", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JoueurMatch> joueurs = new ArrayList<>();
}