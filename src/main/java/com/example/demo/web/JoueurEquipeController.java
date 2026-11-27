package com.example.demo.web;

import com.example.demo.dto.EquipeForm;
import com.example.demo.dto.JoueurForm;
import com.example.demo.dto.ModificationJoueurForm;
import com.example.demo.dto.Vues.EquipeVue;
import com.example.demo.dto.Vues.JoueurVue;
import com.example.demo.service.*;
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

import java.util.Optional;

/**
 * UC05 : l'équipe d'un joueur. Tout joueur peut créer son équipe (il en devient capitaine) et la consulter ;
 * seules les actions du capitaine (ajouter, modifier, retirer, changer de capitaine) sont contrôlées
 * par les services d'après l'état réel en base.
 */
@Controller
@RequestMapping("/joueur/equipe")
@RequiredArgsConstructor
public class JoueurEquipeController {

    private final EquipeService equipeService;
    private final ConsultationService consultationService;
    private final ClubService clubService;

    @GetMapping
    public String monEquipe(@AuthenticationPrincipal CompteUserDetails joueur, Model model) {
        model.addAttribute("equipeForm", new EquipeForm());
        model.addAttribute("joueurForm", new JoueurForm());
        return page(joueur, model);
    }

    @PostMapping("/creer")
    public String creer(@AuthenticationPrincipal CompteUserDetails joueur,
                        @Valid @ModelAttribute("equipeForm") EquipeForm form, BindingResult result,
                        Model model, RedirectAttributes flash) {
        if (!result.hasErrors()) {
            try {
                equipeService.creerEquipe(joueur.getId(), form);
                flash.addFlashAttribute("succes",
                        "Équipe « " + form.getNom().trim() + " » créée : vous en êtes le capitaine");
                return "redirect:/joueur/equipe";
            } catch (RegleMetierException e) {
                result.reject("equipe.invalide", e.getMessage());
            }
        }
        model.addAttribute("joueurForm", new JoueurForm());
        return page(joueur, model);
    }

    /** Le mot de passe temporaire n'est transmis qu'une fois (attribut « flash »), pour le capitaine. */
    @PostMapping("/joueurs")
    public String ajouter(@AuthenticationPrincipal CompteUserDetails capitaine,
                          @Valid @ModelAttribute("joueurForm") JoueurForm form, BindingResult result,
                          Model model, RedirectAttributes flash) {
        if (!result.hasErrors()) {
            try {
                EquipeService.JoueurAjoute ajoute = equipeService.ajouterJoueur(capitaine.getId(), form);
                flash.addFlashAttribute("succes",
                        "Joueur ajouté : " + form.getPrenom().trim() + " " + form.getNom().trim());
                flash.addFlashAttribute("compteCree", CompteService.normaliserEmail(form.getEmail()));
                flash.addFlashAttribute("motDePasseTemporaire", ajoute.motDePasseTemporaire());
                return "redirect:/joueur/equipe";
            } catch (RegleMetierException e) {
                result.reject("joueur.invalide", e.getMessage());
            }
        }
        model.addAttribute("equipeForm", new EquipeForm());
        return page(capitaine, model);
    }

    @GetMapping("/joueurs/{id}/modifier")
    public String modifier(@AuthenticationPrincipal CompteUserDetails capitaine,
                           @PathVariable("id") Long id, Model model) {
        JoueurVue cible = membre(equipeDuCapitaine(capitaine), id);
        ModificationJoueurForm form = new ModificationJoueurForm();
        form.setPoste(cible.poste());
        form.setNumeroMaillot(cible.numeroMaillot());
        model.addAttribute("modificationForm", form);
        model.addAttribute("joueurCible", cible);
        return "joueur/joueur-modifier";
    }

    @PostMapping("/joueurs/{id}/modifier")
    public String enregistrer(@AuthenticationPrincipal CompteUserDetails capitaine,
                              @PathVariable("id") Long id,
                              @Valid @ModelAttribute("modificationForm") ModificationJoueurForm form,
                              BindingResult result, Model model, RedirectAttributes flash) {
        JoueurVue cible = membre(equipeDuCapitaine(capitaine), id);
        if (!result.hasErrors()) {
            try {
                equipeService.modifierJoueur(capitaine.getId(), id, form);
                flash.addFlashAttribute("succes", "Joueur modifié");
                return "redirect:/joueur/equipe";
            } catch (RegleMetierException e) {
                result.reject("joueur.invalide", e.getMessage());
            }
        }
        model.addAttribute("joueurCible", cible);
        return "joueur/joueur-modifier";
    }

    @PostMapping("/joueurs/{id}/retirer")
    public String retirer(@AuthenticationPrincipal CompteUserDetails capitaine,
                          @PathVariable("id") Long id, RedirectAttributes flash) {
        boolean effectifInsuffisant = equipeService.retirerJoueur(capitaine.getId(), id);
        flash.addFlashAttribute("succes", "Joueur retiré de l'équipe");
        if (effectifInsuffisant) {
            flash.addFlashAttribute("avertissement", "Attention : l'équipe compte maintenant moins de "
                    + Regles.EFFECTIF_MINIMUM + " joueurs alors qu'une inscription est en cours : "
                    + "elle ne pourra pas être validée.");
        }
        return "redirect:/joueur/equipe";
    }

    @PostMapping("/joueurs/{id}/capitaine")
    public String nommerCapitaine(@AuthenticationPrincipal CompteUserDetails capitaine,
                                  @PathVariable("id") Long id, RedirectAttributes flash) {
        equipeService.changerCapitaine(capitaine.getId(), id);
        flash.addFlashAttribute("succes", "Nouveau capitaine désigné : vous n'avez plus les droits de capitaine");
        return "redirect:/joueur/equipe";
    }

    // ---- outils ----

    @PostMapping("/logo")
    public String logo(@AuthenticationPrincipal CompteUserDetails joueur,
                       @RequestParam("logo") String logo, RedirectAttributes flash) {
        equipeService.modifierLogo(joueur.getId(), logo);
        flash.addFlashAttribute("succes", "Logo mis à jour");
        return "redirect:/joueur/equipe";
    }

    /** Données de la page : lues en base à chaque affichage, donc toujours à jour. */
    private String page(CompteUserDetails joueur, Model model) {
        Optional<EquipeVue> equipe = consultationService.monEquipe(joueur.getId());
        model.addAttribute("equipe", equipe.orElse(null));
        model.addAttribute("capitaine", OutilsWeb.estCapitaine(equipe.orElse(null), joueur.getId()));
        if (equipe.isEmpty()) {
            model.addAttribute("clubs", clubService.lister());
        }
        return "joueur/equipe";
    }

    private EquipeVue equipeDuCapitaine(CompteUserDetails joueur) {
        EquipeVue equipe = consultationService.monEquipe(joueur.getId())
                .orElseThrow(() -> new AccesRefuseException("Action réservée au capitaine d'une équipe"));
        if (!OutilsWeb.estCapitaine(equipe, joueur.getId())) {
            throw new AccesRefuseException("Action réservée au capitaine d'une équipe");
        }
        return equipe;
    }

    private JoueurVue membre(EquipeVue equipe, Long joueurId) {
        return equipe.joueurs().stream().filter(j -> j.id().equals(joueurId)).findFirst()
                .orElseThrow(() -> new RegleMetierException("Ce joueur n'appartient pas à votre équipe"));
    }
}
