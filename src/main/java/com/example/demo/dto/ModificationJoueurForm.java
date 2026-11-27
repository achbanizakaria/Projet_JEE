package com.example.demo.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/** Seuls le poste et le numéro de maillot d'un joueur sont modifiables par son capitaine. */
@Getter @Setter
public class ModificationJoueurForm {
    @Size(max = 50) private String poste;
    @NotNull @Min(1) @Max(99) private Integer numeroMaillot;
}