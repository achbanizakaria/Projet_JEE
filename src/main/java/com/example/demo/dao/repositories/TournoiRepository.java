package com.example.demo.dao.repositories;

import com.example.demo.dao.entities.StatutTournoi;
import com.example.demo.dao.entities.Tournoi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface TournoiRepository extends JpaRepository<Tournoi, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Tournoi> findTournoiById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Tournoi> findByMatchsId(Long matchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Tournoi> findByInscriptionsId(Long inscriptionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Tournoi> findByGroupesId(Long groupeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Tournoi> findAllByOrderByIdAsc();
    List<Tournoi> findByStatutOrderByDateDebutDesc(StatutTournoi statut);
    List<Tournoi> findByStatutNotOrderByDateDebutDesc(StatutTournoi statut);
    List<Tournoi> findByOrganisateurIdOrderByDateDebutDesc(Long organisateurId);
    boolean existsByNomIgnoreCase(String nom);
}
