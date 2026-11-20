package com.example.demo.dao.enteties;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "matchs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tournoi_id", "numero"}))
@Getter @Setter @NoArgsConstructor
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int numero;

    @Column(name = "date_heure")
    private LocalDateTime dateHeure;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PhaseMatch phase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private StatutMatch statut = StatutMatch.PLANIFIE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournoi_id")
    private Tournoi tournoi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "groupe_id")          // null en phase éliminatoire
    private Groupe groupe;

    // Arbre éliminatoire
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_suivant_victoire_id")
    private Match matchSuivantVictoire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_suivant_defaite_id")   // utilisé par les demi-finales
    private Match matchSuivantDefaite;

    @Column(name = "slot_suivant")
    private Integer slotSuivant;                     // 1 ou 2 : ordre dans le match suivant

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC")
    private List<EquipeMatch> equipeMatchs = new ArrayList<>();
}