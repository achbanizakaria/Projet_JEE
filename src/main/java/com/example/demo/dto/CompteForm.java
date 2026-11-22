package com.example.demo.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CompteForm {
    @NotBlank @Size(max = 80)  private String nom;
    @NotBlank @Size(max = 80)  private String prenom;
    @NotBlank @Email @Size(max = 150) private String email;
    @NotBlank @Size(min = 8, max = 100) private String motDePasse;
}