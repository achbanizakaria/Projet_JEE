package com.example.demo.service;

public final class Regles {
    private Regles() {}

    public static final int EFFECTIF_MINIMUM = 6;
    public static final int NB_TITULAIRES = 5;
    public static final int NB_REMPLACANTS_MAX = 1;
    public static final int POINTS_VICTOIRE = 3;
    public static final int POINTS_NUL = 1;

    /** Tableau à élimination directe + petite finale (si au moins 4 qualifiés). */
    public static int nbMatchsEliminatoires(int totalQualifies) {
        return totalQualifies - 1 + (totalQualifies >= 4 ? 1 : 0);
    }
}