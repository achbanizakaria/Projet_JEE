package com.example.demo.dao.enteties;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "utilisateur")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "role", length = 20)
@Getter @Setter @NoArgsConstructor
public abstract class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(nullable = false, length = 80)
    private String prenom;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "mot_de_passe", nullable = false)
    private String motDePasse; // haché avec BCrypt
}