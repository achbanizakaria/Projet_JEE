package com.example.demo.dao.entities;


import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("ORGANISATEUR")
@Getter @Setter @NoArgsConstructor
public class Organisateur extends Utilisateur {

    @Column(length = 100)
    private String fonction;
}