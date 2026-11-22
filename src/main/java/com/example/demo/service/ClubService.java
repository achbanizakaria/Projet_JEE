package com.example.demo.service;

import com.example.demo.dao.entities.Club;
import com.example.demo.dao.repositories.ClubRepository;
import com.example.demo.dao.repositories.EquipeRepository;
import com.example.demo.dto.ClubForm;
import com.example.demo.dto.Vues.ClubVue;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClubService {

    private final ClubRepository clubRepository;
    private final EquipeRepository equipeRepository;

    @Transactional(readOnly = true)
    public List<ClubVue> lister() {
        return clubRepository.findAll(Sort.by("nom")).stream().map(ClubService::vue).toList();
    }

    @Transactional
    public ClubVue creer(ClubForm form) {
        String nom = form.getNom().trim();
        if (clubRepository.existsByNom(nom)) {
            throw new RegleMetierException("Un club porte déjà ce nom");
        }
        Club c = new Club();
        c.setNom(nom);
        c.setLogo(form.getLogo());
        return vue(clubRepository.save(c));
    }

    @Transactional
    public ClubVue modifier(Long id, ClubForm form) {
        Club c = clubRepository.findById(id)
                .orElseThrow(() -> new RegleMetierException("Club introuvable"));
        String nom = form.getNom().trim();
        if (clubRepository.existsByNomAndIdNot(nom, id)) {
            throw new RegleMetierException("Un club porte déjà ce nom");
        }
        c.setNom(nom);
        c.setLogo(form.getLogo());
        return vue(c);
    }

    @Transactional
    public void supprimer(Long id) {
        Club c = clubRepository.findById(id)
                .orElseThrow(() -> new RegleMetierException("Club introuvable"));
        if (equipeRepository.existsByClubId(id)) {
            throw new RegleMetierException(
                    "Suppression impossible : ce club possède des équipes (déplacez-les ou supprimez-les d'abord)");
        }
        clubRepository.delete(c);
    }

    private static ClubVue vue(Club c) {
        return new ClubVue(c.getId(), c.getNom(), c.getLogo());
    }
}