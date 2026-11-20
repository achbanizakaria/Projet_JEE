package com.example.demo.dao.repositories;

import com.example.demo.dao.entities.EquipeMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipeMatchRepository extends JpaRepository<EquipeMatch, Long> {
    List<EquipeMatch> findByMatchIdOrderByOrdreAsc(Long matchId);
    List<EquipeMatch> findByEquipeId(Long equipeId);
}