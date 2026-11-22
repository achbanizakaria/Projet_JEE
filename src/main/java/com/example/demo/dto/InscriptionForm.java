package com.example.demo.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class InscriptionForm {
    @NotBlank @Size(max = 80)  private String nom;
    @NotBlank @Size(max = 80)  private String prenom;
    @NotBlank @Email @Size(max = 150) private String email;
    @NotBlank @Size(min = 8, max = 100) private String motDePasse;
    @NotBlank private String confirmationMotDePasse;

    /** Conversion vers le formulaire utilisé par les services. */
    public CompteForm versCompte() {
        CompteForm c = new CompteForm();
        c.setNom(nom);
        c.setPrenom(prenom);
        c.setEmail(email);
        c.setMotDePasse(motDePasse);
        return c;
    }
}