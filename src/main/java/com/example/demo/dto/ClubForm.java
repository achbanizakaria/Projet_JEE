package com.example.demo.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ClubForm {
    @NotBlank @Size(max = 100) private String nom;
    @Size(max = 255) private String logo;
}