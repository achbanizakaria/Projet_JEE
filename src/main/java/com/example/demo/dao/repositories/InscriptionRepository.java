package com.example.demo.dao.repositories;

import com.example.demo.dao.entities.Inscription;
import com.example.demo.dao.entities.StatutInscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InscriptionRepository extends JpaRepository<Inscription, Long> {
    List<Inscription> findByTournoiId(Long tournoiId);
    List<Inscription> findByTournoiIdAndStatut(Long tournoiId, StatutInscription statut);
    List<Inscription> findByEquipeId(Long equipeId);
    boolean existsByTournoiIdAndEquipeId(Long tournoiId, Long equipeId);
    long countByTournoiIdAndStatut(Long tournoiId, StatutInscription statut);
}