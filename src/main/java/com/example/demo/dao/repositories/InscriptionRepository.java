package com.example.demo.dao.repositories;

import com.example.demo.dao.entities.Inscription;
import com.example.demo.dao.entities.StatutInscription;
import com.example.demo.dao.entities.StatutTournoi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InscriptionRepository extends JpaRepository<Inscription, Long> {
    List<Inscription> findByTournoiId(Long tournoiId);
    List<Inscription> findByTournoiIdAndStatut(Long tournoiId, StatutInscription statut);
    List<Inscription> findByEquipeId(Long equipeId);
    boolean existsByTournoiIdAndEquipeId(Long tournoiId, Long equipeId);
    long countByTournoiIdAndStatut(Long tournoiId, StatutInscription statut);
    Optional<Inscription> findByTournoiIdAndEquipeId(Long tournoiId, Long equipeId);
    Optional<Inscription> findFirstByTournoiIdAndRangFinal(Long tournoiId, Integer rangFinal);

    // Requêtes dérivées (aucun nom de package écrit en dur)
    boolean existsByEquipeIdAndStatutAndTournoiStatutIn(
            Long equipeId, StatutInscription statut, Collection<StatutTournoi> statuts);

    boolean existsByEquipeIdAndStatutInAndTournoiStatutNot(
            Long equipeId, Collection<StatutInscription> statuts, StatutTournoi statutTournoi);

    /** Équipe validée dans un tournoi dont le statut figure dans la liste. */
    default boolean equipeEngagee(Long equipeId, Collection<StatutTournoi> statuts) {
        return existsByEquipeIdAndStatutAndTournoiStatutIn(equipeId, StatutInscription.VALIDEE, statuts);
    }

    /** Équipe avec une inscription en attente ou validée dans un tournoi non terminé. */
    default boolean equipeAvecInscriptionActive(Long equipeId) {
        return existsByEquipeIdAndStatutInAndTournoiStatutNot(equipeId,
                List.of(StatutInscription.EN_ATTENTE, StatutInscription.VALIDEE), StatutTournoi.TERMINE);
    }
}