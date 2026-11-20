package com.example.demo.dao.repositories;

import com.example.demo.dao.entities.Groupe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupeRepository extends JpaRepository<Groupe, Long> {
    List<Groupe> findByTournoiIdOrderByNomAsc(Long tournoiId);
}