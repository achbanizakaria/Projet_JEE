package com.example.demo.web;

import com.example.demo.dto.TournoiForm;
import com.example.demo.dao.entities.StatutMatch;
import com.example.demo.dao.entities.StatutTournoi;
import com.example.demo.dto.Vues.TournoiDetail;
import com.example.demo.dto.Vues.MatchVue;
import com.example.demo.service.ClassementService;
import com.example.demo.service.ConsultationService;
import com.example.demo.service.PhaseEliminatoireService;
import java.util.List;
import com.example.demo.dto.Vues.TournoiResume;
import com.example.demo.service.CompteUserDetails;
import com.example.demo.service.InscriptionService;
import com.example.demo.service.RegleMetierException;
import com.example.demo.service.TirageService;
import com.example.demo.service.TournoiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** UC10 et UC11 : création des tournois, validation des inscriptions, clôture des inscriptions. */
@Controller
@RequestMapping("/organisateur/tournois")
@RequiredArgsConstructor
public class OrganisateurTournoiController {

    private final TournoiService tournoiService;
    private final InscriptionService inscriptionService;
    private final TirageService tirageService;
    private final ConsultationService consultation;
    private final ClassementService classement;
    private final PhaseEliminatoireService elimination;

    @GetMapping
    public String liste(@AuthenticationPrincipal CompteUserDetails organisateur, Model model) {
        model.addAttribute("tournois", tournoiService.tournoisDe(organisateur.getId()));
        return "organisateur/tournois";
    }

    @GetMapping("/nouveau")
    public String nouveau(Model model) {
        model.addAttribute("tournoiForm", new TournoiForm());
        return "organisateur/tournoi-nouveau";
    }

    @PostMapping("/nouveau")
    public String creer(@AuthenticationPrincipal CompteUserDetails organisateur,
                        @Valid @ModelAttribute("tournoiForm") TournoiForm form, BindingResult result,
                        RedirectAttributes flash) {
        if (!result.hasErrors()) {
            try {
                TournoiResume tournoi = tournoiService.creer(organisateur.getId(), form);
                flash.addFlashAttribute("succes",
                        "Tournoi « " + tournoi.nom() + " » créé : les inscriptions sont ouvertes");
                return "redirect:/organisateur/tournois/" + tournoi.id();
            } catch (RegleMetierException e) {
                result.reject("tournoi.invalide", e.getMessage());
            }
        }
        return "organisateur/tournoi-nouveau";
    }

    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal CompteUserDetails organisateur,
                         @PathVariable("id") Long id, Model model) {
        TournoiDetail tournoi = tournoiService.detail(organisateur.getId(), id);
        model.addAttribute("tournoi", tournoi);
        model.addAttribute("inscriptions", inscriptionService.lister(organisateur.getId(), id));
        List<MatchVue> matchs = consultation.calendrier(id);
        model.addAttribute("matchs", matchs);
        boolean tousJoues = !matchs.isEmpty() && matchs.stream()
                .allMatch(m -> m.statut() == StatutMatch.TERMINE || m.statut() == StatutMatch.FORFAIT);
        model.addAttribute("tousJoues", tousJoues);
        if (tournoi.statut() == StatutTournoi.PHASE_GROUPES && tousJoues) {
            model.addAttribute("aDepartager", consultation.classements(id).stream()
                    .filter(g -> classement.departageNecessaire(g.id(), tournoi.nbQualifiesParGroupe())).toList());
        }
        return "organisateur/tournoi-detail";
    }

    @PostMapping("/{id}/phase-eliminatoire")
    public String generer(@AuthenticationPrincipal CompteUserDetails organisateur, @PathVariable("id") Long id, RedirectAttributes flash) {
        elimination.generer(organisateur.getId(), id);
        flash.addFlashAttribute("succes", "Phase éliminatoire générée");
        return "redirect:/organisateur/tournois/" + id;
    }

    @PostMapping("/{id}/groupes/{groupeId}/departager")
    public String departager(@AuthenticationPrincipal CompteUserDetails organisateur, @PathVariable("id") Long id,
                             @PathVariable("groupeId") Long groupeId, RedirectAttributes flash) {
        classement.departagerParTirage(organisateur.getId(), id, groupeId);
        flash.addFlashAttribute("succes", "Tirage de départage effectué");
        return "redirect:/organisateur/tournois/" + id;
    }

    @PostMapping("/{id}/cloturer")
    public String cloturer(@AuthenticationPrincipal CompteUserDetails organisateur, @PathVariable("id") Long id, RedirectAttributes flash) {
        tournoiService.cloturer(organisateur.getId(), id);
        flash.addFlashAttribute("succes", "Tournoi clôturé et ajouté à l’historique");
        return "redirect:/consultation/tournois";
    }

    @PostMapping("/{id}/inscriptions/{inscriptionId}/valider")
    public String valider(@AuthenticationPrincipal CompteUserDetails organisateur,
                          @PathVariable("id") Long id, @PathVariable("inscriptionId") Long inscriptionId,
                          RedirectAttributes flash) {
        inscriptionService.valider(organisateur.getId(), id, inscriptionId);
        flash.addFlashAttribute("succes", "Inscription validée");
        return "redirect:/organisateur/tournois/" + id;
    }

    @PostMapping("/{id}/inscriptions/{inscriptionId}/refuser")
    public String refuser(@AuthenticationPrincipal CompteUserDetails organisateur,
                          @PathVariable("id") Long id, @PathVariable("inscriptionId") Long inscriptionId,
                          @RequestParam(name = "motif", defaultValue = "") String motif,
                          RedirectAttributes flash) {
        inscriptionService.refuser(organisateur.getId(), id, inscriptionId, motif);
        flash.addFlashAttribute("succes", "Inscription refusée");
        return "redirect:/organisateur/tournois/" + id;
    }
    @PostMapping("/{id}/inscriptions/{inscriptionId}/retirer")
    public String retirer(@AuthenticationPrincipal CompteUserDetails organisateur,
                          @PathVariable("id") Long id, @PathVariable("inscriptionId") Long inscriptionId,
                          @RequestParam("motif") String motif, RedirectAttributes flash) {
        inscriptionService.retirer(organisateur.getId(), id, inscriptionId, motif);
        flash.addFlashAttribute("succes", "Retrait enregistré. Vous pouvez repêcher une équipe refusée.");
        return "redirect:/organisateur/tournois/" + id;
    }

    @PostMapping("/{id}/rouvrir-inscriptions")
    public String rouvrirInscriptions(@AuthenticationPrincipal CompteUserDetails organisateur,
                                      @PathVariable("id") Long id, RedirectAttributes flash) {
        tournoiService.rouvrirInscriptions(organisateur.getId(), id);
        flash.addFlashAttribute("succes", "Les inscriptions sont rouvertes");
        return "redirect:/organisateur/tournois/" + id;
    }

    /** Tirage au sort des groupes et génération du calendrier des poules (une seule transaction). */
    @PostMapping("/{id}/tirage")
    public String tirage(@AuthenticationPrincipal CompteUserDetails organisateur,
                         @PathVariable("id") Long id, RedirectAttributes flash) {
        tirageService.effectuerTirage(organisateur.getId(), id);
        flash.addFlashAttribute("succes", "Tirage au sort effectué : groupes constitués et calendrier des poules généré");
        return "redirect:/organisateur/tournois/" + id;
    }

    /** Possible tant qu'aucun résultat n'est saisi : ramène le tournoi à « inscriptions closes ». */
    @PostMapping("/{id}/annuler-tirage")
    public String annulerTirage(@AuthenticationPrincipal CompteUserDetails organisateur,
                                @PathVariable("id") Long id, RedirectAttributes flash) {
        tirageService.annulerTirage(organisateur.getId(), id);
        flash.addFlashAttribute("succes", "Tirage annulé : les groupes et le calendrier ont été supprimés");
        return "redirect:/organisateur/tournois/" + id;
    }

    @PostMapping("/{id}/cloturer-inscriptions")
    public String cloturerInscriptions(@AuthenticationPrincipal CompteUserDetails organisateur,
                                       @PathVariable("id") Long id, RedirectAttributes flash) {
        tournoiService.cloturerInscriptions(organisateur.getId(), id);
        flash.addFlashAttribute("succes", "Les inscriptions sont closes : le tirage au sort est possible");
        return "redirect:/organisateur/tournois/" + id;
    }
}
