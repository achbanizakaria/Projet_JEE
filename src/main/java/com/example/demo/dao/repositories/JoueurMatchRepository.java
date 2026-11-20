package com.example.demo.dao.repositories;

import com.example.demo.dao.entities.JoueurMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JoueurMatchRepository extends JpaRepository<JoueurMatch, Long> {
    List<JoueurMatch> findByEquipeMatchId(Long equipeMatchId);
}