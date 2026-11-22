package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class JoueurStatForm {
    @NotNull private Long joueurId;
    private boolean estTitulaire = true;
    private int buts;
    private int cartonsJaunes;
    private boolean cartonRouge;
    private Integer minuteEntree;
    private Integer minuteSortie;
}