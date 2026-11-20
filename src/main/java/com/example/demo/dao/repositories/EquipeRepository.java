package com.example.demo.dao.repositories;

import com.example.demo.dao.entities.Equipe;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipeRepository extends JpaRepository<Equipe, Long> {
    boolean existsByNom(String nom);
    boolean existsByClubId(Long clubId);
}