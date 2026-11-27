package com.example.demo.service;

import com.example.demo.dao.entities.*;
import com.example.demo.dao.repositories.*;
import com.example.demo.dto.EquipeForm;
import com.example.demo.dto.JoueurForm;
import com.example.demo.dto.ModificationJoueurForm;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;
@Service
@RequiredArgsConstructor
public class EquipeService {

    private static final Set<StatutTournoi> TIRAGE_FAIT = EnumSet.of(
            StatutTournoi.TIRAGE_EFFECTUE, StatutTournoi.PHASE_GROUPES, StatutTournoi.PHASE_ELIMINATOIRE);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    /** Le mot de passe temporaire (en clair) n'est montré qu'une fois, au capitaine. */
    public record JoueurAjoute(Long joueurId, String motDePasseTemporaire) {}

    private final EquipeRepository equipeRepository;
    private final ClubRepository clubRepository;
    private final JoueurRepository joueurRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final InscriptionRepository inscriptionRepository;
    private final ControleAcces acces;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    /** Le créateur de l'équipe en devient le capitaine. Retourne l'id de l'équipe. */
    @Transactional
    public Long creerEquipe(Long joueurId, EquipeForm form) {
        Joueur createur = acces.joueur(joueurId);
        if (createur.getEquipe() != null) {
            throw new RegleMetierException("Vous appartenez déjà à une équipe");
        }
        String nom = form.getNom().trim();
        if (equipeRepository.existsByNom(nom)) {
            throw new RegleMetierException("Une équipe porte déjà ce nom");
        }
        Club club = clubRepository.findById(form.getClubId())
                .orElseThrow(() -> new RegleMetierException("Club introuvable"));
        Equipe equipe = new Equipe();
        equipe.setNom(nom);
        equipe.setLogo(logoDisponible(form.getLogo(), 0L));
        equipe.setClub(club);
        enregistrerEquipe(equipe);
        createur.setEquipe(equipe);
        createur.setEstCapitaine(true);
        return equipe.getId();
    }

    /**
     * Crée le compte du joueur (UC05) avec un mot de passe temporaire à changer à la première connexion.
     * estCapitaine est toujours forcé à false ; l'équipe est celle du capitaine connecté.
     * Une adresse e-mail déjà utilisée est refusée : on ne rattache jamais un compte existant sans son accord.
     */
    @Transactional
    public JoueurAjoute ajouterJoueur(Long capitaineId, JoueurForm form) {
        Joueur capitaine = acces.capitaine(capitaineId);
        Equipe equipe = capitaine.getEquipe();
        verifierEquipeModifiable(equipe.getId());
        verifierMaillotLibre(equipe.getId(), form.getNumeroMaillot(), null);

        String email = CompteService.normaliserEmail(form.getEmail());
        if (utilisateurRepository.existsByEmail(email)) {
            throw new RegleMetierException("Cette adresse e-mail est déjà utilisée par un compte existant");
        }
        String motDePasse = genererMotDePasse();
        Joueur joueur = new Joueur();
        joueur.setNom(form.getNom().trim());
        joueur.setPrenom(form.getPrenom().trim());
        joueur.setEmail(email);
        joueur.setMotDePasse(passwordEncoder.encode(motDePasse));
        joueur.setMotDePasseTemporaire(true);
        joueur.setPoste(form.getPoste());
        joueur.setNumeroMaillot(form.getNumeroMaillot());
        joueur.setEstCapitaine(false);
        joueur.setEquipe(equipe);
        joueurRepository.save(joueur);
        return new JoueurAjoute(joueur.getId(), motDePasse);
    }

    /** Seuls le poste et le numéro de maillot sont modifiables. */
    @Transactional
    public void modifierJoueur(Long capitaineId, Long joueurId, ModificationJoueurForm form) {
        Joueur capitaine = acces.capitaine(capitaineId);
        Joueur joueur = membre(capitaine, joueurId);
        verifierEquipeModifiable(capitaine.getEquipe().getId());
        verifierMaillotLibre(capitaine.getEquipe().getId(), form.getNumeroMaillot(), joueurId);
        joueur.setPoste(form.getPoste());
        joueur.setNumeroMaillot(form.getNumeroMaillot());
    }

    /**
     * Retire un joueur de l'équipe (son compte est conservé).
     * @return true si l'effectif tombe sous le minimum alors qu'une inscription est active (avertissement).
     */
    @Transactional
    public boolean retirerJoueur(Long capitaineId, Long joueurId) {
        Joueur capitaine = acces.capitaine(capitaineId);
        Joueur joueur = membre(capitaine, joueurId);
        Long equipeId = capitaine.getEquipe().getId();
        if (joueur.isEstCapitaine()) {
            throw new RegleMetierException("Le capitaine ne peut pas être retiré : désignez d'abord un autre capitaine");
        }
        verifierEquipeModifiable(equipeId);
        long effectifApres = joueurRepository.countByEquipeId(equipeId) - 1;
        joueur.setEquipe(null);
        joueur.setNumeroMaillot(null);
        return effectifApres < Regles.EFFECTIF_MINIMUM
                && inscriptionRepository.equipeAvecInscriptionActive(equipeId);
    }

    /** Seul point de l'application où l'on transfère le rôle de capitaine. */
    @Transactional
    public void changerCapitaine(Long capitaineId, Long nouveauCapitaineId) {
        Joueur ancien = acces.capitaine(capitaineId);
        Joueur nouveau = membre(ancien, nouveauCapitaineId);
        if (nouveau.getId().equals(ancien.getId())) {
            throw new RegleMetierException("Ce joueur est déjà capitaine");
        }
        ancien.setEstCapitaine(false);
        nouveau.setEstCapitaine(true);
    }

    // ---- contrôles ----

    @Transactional
    public void modifierLogo(Long capitaineId, String logo) {
        Equipe equipe = acces.capitaine(capitaineId).getEquipe();
        equipe.setLogo(logoDisponible(logo, equipe.getId()));
        if (equipe.getLogo() != null) equipe.setAncienLogoPartage(null);
        enregistrerEquipe(equipe);
    }

    /** Reprise des anciennes équipes, dont le champ logo existait avant sa clé unique. */
    @Transactional
    public void regulariserAnciensLogos() {
        List<Equipe> equipes = equipeRepository.findAllByOrderByIdAsc();
        if (equipes.stream().noneMatch(e -> e.getLogo() != null && e.getLogoCle() == null)) return;

        // Une équipe récente peut déjà détenir la clé d'une équipe plus ancienne.
        // La libérer avant la réattribution évite un conflit transitoire dans l'index unique.
        equipes.forEach(e -> e.setLogoCle(null));
        equipeRepository.flush();
        for (Equipe e : equipes) {
            String logo = e.getLogo();
            String cle = Equipe.cleLogo(logo);
            if (cle != null && equipeRepository.existsByLogoCleAndIdNot(cle, e.getId())) {
                e.setAncienLogoPartage(logo);
                e.setLogo(null);
            } else {
                e.setLogo(logo);
            }
        }
    }

    private String logoDisponible(String valeur, Long exclue) {
        if (valeur == null || valeur.isBlank()) return null;
        String logo = valeur.strip();
        if (logo.length() > 255) throw new RegleMetierException("Le logo dépasse 255 caractères");
        if (equipeRepository.existsByLogoCleAndIdNot(Equipe.cleLogo(logo), exclue)) {
            throw new RegleMetierException("Ce logo est déjà utilisé par une autre équipe");
        }
        return logo;
    }

    private void enregistrerEquipe(Equipe equipe) {
        try {
            equipeRepository.saveAndFlush(equipe);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT).contains("uk_equipe_logo_cle")) {
                throw new RegleMetierException("Ce logo est déjà utilisé par une autre équipe");
            }
            throw e;
        }
    }

    private Joueur membre(Joueur capitaine, Long joueurId) {
        Joueur j = acces.joueur(joueurId);
        if (j.getEquipe() == null || !j.getEquipe().getId().equals(capitaine.getEquipe().getId())) {
            throw new AccesRefuseException("Ce joueur n'appartient pas à votre équipe");
        }
        return j;
    }

    private void verifierEquipeModifiable(Long equipeId) {
        if (inscriptionRepository.equipeEngagee(equipeId, TIRAGE_FAIT)) {
            throw new RegleMetierException(
                    "Modification impossible : l'équipe est engagée dans un tournoi dont le tirage a eu lieu");
        }
    }

    private void verifierMaillotLibre(Long equipeId, Integer numero, Long joueurExclu) {
        boolean pris = joueurExclu == null
                ? joueurRepository.existsByEquipeIdAndNumeroMaillot(equipeId, numero)
                : joueurRepository.existsByEquipeIdAndNumeroMaillotAndIdNot(equipeId, numero, joueurExclu);
        if (pris) {
            throw new RegleMetierException("Ce numéro de maillot est déjà pris dans l'équipe");
        }
    }

    private String genererMotDePasse() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
