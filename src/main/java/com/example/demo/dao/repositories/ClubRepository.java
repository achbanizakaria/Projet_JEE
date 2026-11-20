package com.example.demo.dao.repositories;

import com.example.demo.dao.entities.Club;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClubRepository extends JpaRepository<Club, Long> {
    boolean existsByNom(String nom);
}