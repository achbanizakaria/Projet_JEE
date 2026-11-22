package com.example.demo.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class CompositionForm {
    private List<Long> titulaires = new ArrayList<>();
    private Long remplacant;   // facultatif
}