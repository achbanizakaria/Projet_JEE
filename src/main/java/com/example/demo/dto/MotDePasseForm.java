package com.example.demo.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class MotDePasseForm {
    @NotBlank private String ancienMotDePasse;
    @NotBlank @Size(min = 8, max = 100) private String nouveauMotDePasse;
    @NotBlank private String confirmationNouveauMotDePasse;
}