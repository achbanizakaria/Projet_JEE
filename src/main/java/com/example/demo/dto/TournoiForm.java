package com.example.demo.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter @Setter
public class TournoiForm {
    @NotBlank @Size(max = 120) private String nom;
    @NotBlank @Size(max = 50)  private String sport;
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate dateDebut;
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate dateFin;
    @NotNull @Min(1) private Integer nbGroupes;
    @NotNull @Min(1) private Integer nbQualifiesParGroupe;
    @NotNull @Min(1) private Integer dureeMatchMinutes = 60;
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) private LocalTime heureOuverture = LocalTime.of(14, 0);
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) private LocalTime heureFermeture = LocalTime.of(20, 0);
    @NotNull @Min(0) private Integer reposMinutes = 0;
    @NotNull @Min(0) private Integer scoreForfait = 3;
}