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
    @NotNull @Min(1) private Integer nbGroupes=4;
    @NotNull @Min(1) private Integer nbQualifiesParGroupe=2;
    @NotNull @Min(1) private Integer dureeMatchMinutes = 30;
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) private LocalTime heureOuverture = LocalTime.of(12, 30);
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) private LocalTime heureFermeture = LocalTime.of(14, 30);
    @NotNull @Min(0) private Integer reposMinutes = 0;
    @NotNull @Min(0) private Integer scoreForfait = 3;
}