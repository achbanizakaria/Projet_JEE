package com.example.demo.dao.entities;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"tournoi_id", "equipe_id"}))
@Getter @Setter @NoArgsConstructor
public class Inscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date_inscription", nullable = false)
    private LocalDate dateInscription = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private StatutInscription statut = StatutInscription.EN_ATTENTE;

    @Column(name = "rang_final")
    private Integer rangFinal;

    @Column(name = "motif_refus")
    private String motifRefus;

    @Column(name = "motif_retrait")
    private String motifRetrait;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournoi_id")
    private Tournoi tournoi;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipe_id")
    private Equipe equipe;
}
