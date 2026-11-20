package com.example.demo.dao.repositories;

import com.example.demo.dao.entities.StatutTournoi;
import com.example.demo.dao.entities.Tournoi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TournoiRepository extends JpaRepository<Tournoi, Long> {
    List<Tournoi> findByStatutOrderByDateDebutDesc(StatutTournoi statut);
    List<Tournoi> findByStatutNotOrderByDateDebutDesc(StatutTournoi statut);
}