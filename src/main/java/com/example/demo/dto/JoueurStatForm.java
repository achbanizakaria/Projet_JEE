package com.example.demo.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class JoueurStatForm {
    private Long joueurId; // facultatif uniquement pour la ligne de remplaçant laissée entièrement vide
    private boolean estTitulaire = true;
    private int buts;
    private int cartonsJaunes;
    private boolean cartonRouge;
    private Integer minuteEntree;
    private Integer minuteSortie;
}
