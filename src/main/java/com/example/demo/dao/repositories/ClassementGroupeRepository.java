package com.example.demo.dao.repositories;

import com.example.demo.dao.entities.ClassementGroupe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClassementGroupeRepository extends JpaRepository<ClassementGroupe, Long> {
    List<ClassementGroupe> findByGroupeId(Long groupeId);
    Optional<ClassementGroupe> findByGroupeIdAndEquipeId(Long groupeId, Long equipeId);
}