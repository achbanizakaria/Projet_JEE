package com.example.demo.dao.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("JOUEUR")
@Getter @Setter @NoArgsConstructor
public class Joueur extends Utilisateur {

    @Column(length = 50)
    private String poste;

    @Column(name = "numero_maillot")
    private Integer numeroMaillot;

    @Column(name = "est_capitaine", columnDefinition = "boolean default false")
    private boolean estCapitaine = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipe_id")
    private Equipe equipe;
}