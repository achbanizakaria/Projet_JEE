package com.example.demo.dao.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_equipe_logo_cle", columnNames = "logo_cle"))
@Getter @Setter @NoArgsConstructor
public class Equipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nom;

    private String logo;

    // Colonne distincte pour que les anciens logos (y compris les chaînes vides) restent lisibles.
    @Column(name = "logo_cle", length = 512)
    private String logoCle;

    @Column(name = "ancien_logo_partage")
    private String ancienLogoPartage;

    public void setLogo(String valeur) {
        logo = valeur == null || valeur.isBlank() ? null : valeur.strip();
        logoCle = cleLogo(logo);
    }

    public static String cleLogo(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.strip().toLowerCase(Locale.ROOT);
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id")
    private Club club;

    @OneToMany(mappedBy = "equipe")
    private List<Joueur> joueurs = new ArrayList<>();
}
