package com.example.demo.dao.repositories;

import com.example.demo.dao.entities.Equipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.List;

public interface EquipeRepository extends JpaRepository<Equipe, Long> {
    boolean existsByNom(String nom);
    boolean existsByLogoCleAndIdNot(String logoCle, Long exclue);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Equipe> findAllByOrderByIdAsc();
    boolean existsByClubId(Long clubId);
}
