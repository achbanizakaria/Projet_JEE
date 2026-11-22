package com.example.demo.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class JoueurForm {
    @NotBlank @Size(max = 80)  private String nom;
    @NotBlank @Size(max = 80)  private String prenom;
    @NotBlank @Email @Size(max = 150) private String email;
    @Size(max = 50) private String poste;
    @NotNull @Min(1) @Max(99) private Integer numeroMaillot;
}