package com.example.demo.dao.repositories;

import com.example.demo.dao.entities.Joueur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JoueurRepository extends JpaRepository<Joueur, Long> {
    List<Joueur> findByEquipeId(Long equipeId);
    long countByEquipeId(Long equipeId);
    Optional<Joueur> findByEquipeIdAndEstCapitaineTrue(Long equipeId);
    boolean existsByEquipeIdAndEstCapitaineTrue(Long equipeId);
    boolean existsByEquipeIdAndNumeroMaillot(Long equipeId, Integer numeroMaillot);
    long countByEquipeIdAndEstCapitaineTrue(Long equipeId);
    boolean existsByEquipeIdAndNumeroMaillotAndIdNot(Long equipeId, Integer numeroMaillot, Long id);
}