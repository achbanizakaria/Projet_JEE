package com.example.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class ResultatForm {
    @NotNull @Min(0) private Integer scoreEquipe1;
    @NotNull @Min(0) private Integer scoreEquipe2;
    private Integer tirsAuBut1;
    private Integer tirsAuBut2;
    @Valid private List<JoueurStatForm> joueursEquipe1 = new ArrayList<>();
    @Valid private List<JoueurStatForm> joueursEquipe2 = new ArrayList<>();
}