package com.example.demo.web;

import com.example.demo.dao.entities.*;
import com.example.demo.dto.CompositionForm;
import com.example.demo.dto.EquipeForm;
import com.example.demo.dao.repositories.InscriptionRepository;
import com.example.demo.dto.JoueurStatForm;
import com.example.demo.dto.ResultatForm;
import com.example.demo.dto.Vues.MatchVue;
import com.example.demo.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TirageConsultationTests {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate transactions;
    @Autowired ConsultationService consultation;
    @Autowired MatchService matchs;
    @Autowired EquipeService equipes;
    @Autowired InscriptionRepository inscriptions;

    private record Coupe(Long id, CompteUserDetails responsable) {
        String gestion() { return "/organisateur/tournois/" + id; }
        String consultation() { return "/consultation/tournois/" + id; }
    }

    @Test
    void consultationReserveeAuxConnectesEtTirageAuResponsable() throws Exception {
        Coupe coupe = creerCoupe("Droits");
        CompteUserDetails joueur = CompteUserDetails.de(new Joueur());
        Organisateur autre = new Organisateur();
        autre.setId(-1L);
        mvc.perform(get(coupe.consultation()))
                .andExpect(redirectedUrl("/connexion"));
        mvc.perform(get(coupe.consultation()).with(user(joueur)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("après le tirage")));
        mvc.perform(post(coupe.gestion() + "/tirage").with(user(joueur)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post(coupe.gestion() + "/tirage").with(user(CompteUserDetails.de(autre))).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post(coupe.gestion() + "/tirage").with(user(coupe.responsable())))
                .andExpect(status().isForbidden());
        mvc.perform(post(coupe.gestion() + "/tirage").with(user(coupe.responsable())).with(csrf())
                        .header("Referer", "http://localhost" + coupe.gestion()))
                .andExpect(redirectedUrl(coupe.gestion()))
                .andExpect(flash().attribute("erreur", containsString("au moins 2 équipes validées")));
        assertThat(consultation.classements(coupe.id())).isEmpty();
        assertThat(consultation.calendrier(coupe.id())).isEmpty();
    }

    @Test
    void tirageConsultablePuisAnnulableAvantLePremierResultat() throws Exception {
        Coupe coupe = creerCoupe("Annulation");
        ajouterEquipe(coupe, "Lions");
        ajouterEquipe(coupe, "Atlas");
        mvc.perform(get(coupe.gestion()).with(user(coupe.responsable())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2</span> équipe(s) validée(s)")))
                .andExpect(content().string(containsString(coupe.gestion() + "/tirage")));
        MatchVue match = tirer(coupe);
        mvc.perform(get(coupe.gestion()).with(user(coupe.responsable())))
                .andExpect(content().string(containsString("Tirage effectué")))
                .andExpect(content().string(containsString(coupe.gestion() + "/annuler-tirage")));
        mvc.perform(get(coupe.consultation()).with(user(coupe.responsable())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Lions")))
                .andExpect(content().string(containsString("Atlas")))
                .andExpect(content().string(containsString("Groupe A")))
                .andExpect(content().string(containsString("/consultation/matchs/" + match.id())));
        mvc.perform(get("/consultation/matchs/{id}", match.id()).with(user(coupe.responsable())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Planifié")))
                .andExpect(content().string(containsString("Composition non renseignée.")))
                .andExpect(content().string(containsString("href=\"" + coupe.consultation() + "\"")));
        mvc.perform(post(coupe.gestion() + "/annuler-tirage").with(user(coupe.responsable())).with(csrf()))
                .andExpect(redirectedUrl(coupe.gestion())).andExpect(flash().attributeExists("succes"));
        assertThat(consultation.tournoi(coupe.id()).statut()).isEqualTo(StatutTournoi.INSCRIPTION_CLOSE);
        assertThat(consultation.classements(coupe.id())).isEmpty();
        assertThat(consultation.calendrier(coupe.id())).isEmpty();
    }

    @Test
    void feuilleTableauEtHistoriqueAffichentLesDonneesDuTournoi() throws Exception {
        Coupe coupe = creerCoupe("Palmarès");
        List<Joueur> locaux = ajouterEquipe(coupe, "Faucons");
        List<Joueur> visiteurs = ajouterEquipe(coupe, "Aigles");
        MatchVue poule = tirer(coupe);
        CompositionForm composition = new CompositionForm();
        composition.setTitulaires(locaux.subList(0, 5).stream().map(Joueur::getId).toList());
        composition.setRemplacant(locaux.get(5).getId());
        CompteUserDetails capitaine = CompteUserDetails.de(locaux.getFirst());
        String urlComposition = "/joueur/matchs/" + poule.id() + "/composition";
        mvc.perform(get("/joueur/matchs").with(user(capitaine))).andExpect(status().isOk())
                .andExpect(content().string(containsString("/consultation/matchs/" + poule.id())));
        mvc.perform(get(urlComposition).with(user(capitaine))).andExpect(status().isOk())
                .andExpect(content().string(containsString("J6 Faucons")));
        mvc.perform(get(urlComposition).with(user(CompteUserDetails.de(locaux.get(1))))).andExpect(status().isForbidden());
        mvc.perform(post(urlComposition).with(user(capitaine)).with(csrf())
                        .param("titulaires", composition.getTitulaires().stream().map(String::valueOf).toArray(String[]::new))
                        .param("remplacant", composition.getRemplacant().toString()))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(get("/organisateur/matchs/{id}/resultat", poule.id()).with(user(coupe.responsable())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("participants de la composition enregistrée")));
        mvc.perform(post("/organisateur/matchs/{id}/forfait/{equipe}", poule.id(), visiteurs.getFirst().getEquipe().getId())
                        .with(user(coupe.responsable())).with(csrf()).param("motif", "Équipe indisponible"))
                .andExpect(flash().attributeExists("succes"));
        CompteUserDetails joueur = CompteUserDetails.de(locaux.get(1));
        mvc.perform(get("/consultation/matchs/{id}", poule.id()).with(user(joueur)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Forfait")))
                .andExpect(content().string(containsString("J6 Faucons")))
                .andExpect(content().string(containsString("Remplaçant")));
        mvc.perform(get(coupe.gestion()).with(user(coupe.responsable())))
                .andExpect(content().string(not(containsString("/annuler-tirage"))));
        mvc.perform(post(coupe.gestion() + "/annuler-tirage").with(user(coupe.responsable())).with(csrf()))
                .andExpect(flash().attribute("erreur", containsString("Annulation impossible")));

        Long responsable = coupe.responsable().getId();
        mvc.perform(post(coupe.gestion() + "/phase-eliminatoire").with(user(coupe.responsable())).with(csrf()))
                .andExpect(flash().attributeExists("succes"));
        MatchVue finale = consultation.tableauEliminatoire(coupe.id()).getFirst();
        mvc.perform(get(coupe.consultation()).with(user(joueur)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Phase éliminatoire")))
                .andExpect(content().string(containsString("/consultation/matchs/" + finale.id())));
        boolean fauconsEnPremier = finale.equipe1().equals("Faucons");
        ResultatForm resultat = matchs.formulaireResultat(responsable, finale.id());
        resultat.setScoreEquipe1(1);
        resultat.setScoreEquipe2(1);
        resultat.setJoueursEquipe1(feuilleDeFinale(fauconsEnPremier ? locaux : visiteurs));
        resultat.setJoueursEquipe2(feuilleDeFinale(fauconsEnPremier ? visiteurs : locaux));
        resultat.setTirsAuBut1(fauconsEnPremier ? 4 : 3);
        resultat.setTirsAuBut2(fauconsEnPremier ? 3 : 4);
        String urlResultat = "/organisateur/matchs/" + finale.id() + "/resultat";
        mvc.perform(get(urlResultat).with(user(coupe.responsable()))).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"joueursEquipe1[5].joueurId\"")))
                .andExpect(content().string(containsString("name=\"joueursEquipe2[5].cartonsJaunes\"")));
        MockHttpServletRequestBuilder saisie = post(urlResultat).with(user(coupe.responsable())).with(csrf())
                .param("tirsAuBut1", resultat.getTirsAuBut1().toString()).param("tirsAuBut2", resultat.getTirsAuBut2().toString())
                .param("scoreEquipe1", "1").param("scoreEquipe2", "1");
        ajouterLignes(saisie, "joueursEquipe1", resultat.getJoueursEquipe1());
        ajouterLignes(saisie, "joueursEquipe2", resultat.getJoueursEquipe2());
        mvc.perform(saisie).andExpect(redirectedUrl("/consultation/matchs/" + finale.id()));
        mvc.perform(get("/consultation/matchs/{id}", finale.id()).with(user(joueur)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("1 – 1")))
                .andExpect(content().string(containsString("Tirs au but : " + resultat.getTirsAuBut1()
                        + " – " + resultat.getTirsAuBut2())))
                .andExpect(content().string(containsString("15’")));
        mvc.perform(post(coupe.gestion() + "/cloturer").with(user(coupe.responsable())).with(csrf()))
                .andExpect(redirectedUrl("/consultation/tournois"));
        mvc.perform(get("/consultation/tournois").with(user(joueur)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Historique")))
                .andExpect(content().string(containsString("<td>Faucons</td>")))
                .andExpect(content().string(containsString("<td>Aigles</td>")));
        assertThat(consultation.historique()).anySatisfy(t -> {
            assertThat(t.id()).isEqualTo(coupe.id());
            assertThat(t.champion()).isEqualTo("Faucons");
            assertThat(t.finaliste()).isEqualTo("Aigles");
        });
    }

    private void ajouterLignes(MockHttpServletRequestBuilder requete, String liste, List<JoueurStatForm> lignes) {
        for (int i = 0; i < lignes.size(); i++) {
            String champ = liste + "[" + i + "].";
            JoueurStatForm l = lignes.get(i);
            requete.param(champ + "joueurId", l.getJoueurId().toString())
                    .param(champ + "estTitulaire", String.valueOf(l.isEstTitulaire())).param(champ + "buts", String.valueOf(l.getButs()));
            if (l.getMinuteEntree() != null) requete.param(champ + "minuteEntree", l.getMinuteEntree().toString());
            if (l.getMinuteSortie() != null) requete.param(champ + "minuteSortie", l.getMinuteSortie().toString());
        }
    }

    @Test
    void retraitEtRepechageAvantLeTirageSeulement() throws Exception {
        Coupe coupe = creerCoupe("Remplacement");
        List<Joueur> sortants = ajouterEquipe(coupe, "Sortants");
        List<Joueur> entrants = ajouterEquipe(coupe, "Entrants");
        ajouterEquipe(coupe, "Campus");
        Inscription sortante = inscriptions.findByTournoiIdAndEquipeId(coupe.id(), sortants.getFirst().getEquipe().getId()).orElseThrow();
        Inscription entrante = inscriptions.findByTournoiIdAndEquipeId(coupe.id(), entrants.getFirst().getEquipe().getId()).orElseThrow();
        transactions.executeWithoutResult(s -> {
            Inscription i = em.find(Inscription.class, entrante.getId());
            i.setStatut(StatutInscription.REFUSEE);
            i.setMotifRefus("Effectif incomplet lors de la demande");
        });
        String retrait = coupe.gestion() + "/inscriptions/" + sortante.getId() + "/retirer";
        String repechage = coupe.gestion() + "/inscriptions/" + entrante.getId() + "/valider";
        mvc.perform(get(coupe.gestion()).with(user(coupe.responsable()))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Repêcher l’équipe")));
        mvc.perform(post(retrait).with(user(coupe.responsable())).with(csrf()).param("motif", "Indisponibilité"))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(post(repechage).with(user(coupe.responsable())).with(csrf())).andExpect(flash().attributeExists("succes"));
        mvc.perform(get("/joueur/tournois").with(user(CompteUserDetails.de(sortants.getFirst()))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Retirée")))
                .andExpect(content().string(containsString("Indisponibilité")));
        assertThat(inscriptions.findById(sortante.getId()).orElseThrow().getStatut()).isEqualTo(StatutInscription.FORFAIT);
        assertThat(inscriptions.findById(entrante.getId()).orElseThrow().getMotifRefus()).isNull();
        MatchVue match = tirer(coupe);
        assertThat(List.of(match.equipe1(), match.equipe2())).containsExactlyInAnyOrder("Entrants", "Campus");
        mvc.perform(post(coupe.gestion() + "/inscriptions/" + entrante.getId() + "/retirer").with(user(coupe.responsable()))
                .with(csrf()).param("motif", "Retrait tardif")).andExpect(flash().attributeExists("erreur"));
    }

    @Test
    void preparationDesParticipantsEtResultatSansRemplacant() throws Exception {
        Coupe coupe = creerCoupe("Feuilles");
        List<Joueur> a = ajouterEquipe(coupe, "Rif");
        List<Joueur> b = ajouterEquipe(coupe, "Souss");
        MatchVue m = tirer(coupe);
        String gestion = "/organisateur/matchs/" + m.id();
        String preparer = gestion + "/equipes/" + a.getFirst().getEquipe().getId() + "/composition";
        String consultationMatch = "/consultation/matchs/" + m.id();
        String[] cinq = a.stream().limit(5).map(j -> j.getId().toString()).toArray(String[]::new);
        mvc.perform(get(consultationMatch).with(user(coupe.responsable())))
                .andExpect(content().string(containsString(preparer)));
        mvc.perform(get(preparer).with(user(coupe.responsable()))).andExpect(status().isOk())
                .andExpect(content().string(containsString("J1 Rif")))
                .andExpect(content().string(not(containsString("J1 Souss"))));
        String[] avecAdversaire = cinq.clone();
        avecAdversaire[0] = b.getFirst().getId().toString();
        mvc.perform(post(preparer).with(user(coupe.responsable())).with(csrf()).param("titulaires", avecAdversaire))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("compositionForm"));
        assertThat(matchs.compositionOrganisateur(coupe.responsable().getId(), m.id(), a.getFirst().getEquipe().getId()).getTitulaires()).isEmpty();
        mvc.perform(post(preparer).with(user(coupe.responsable())).with(csrf()).param("titulaires", cinq))
                .andExpect(redirectedUrl(consultationMatch));
        String capitaine = "/joueur/matchs/" + m.id() + "/composition";
        mvc.perform(post(capitaine).with(user(CompteUserDetails.de(a.getFirst()))).with(csrf()).param("titulaires", cinq))
                .andExpect(flash().attributeExists("succes"));
        mvc.perform(get(consultationMatch).with(user(coupe.responsable())))
                .andExpect(content().string(not(containsString(preparer))));
        mvc.perform(post(preparer).with(user(coupe.responsable())).with(csrf()).param("titulaires", cinq))
                .andExpect(flash().attributeExists("erreur"));

        MockHttpServletRequestBuilder score = post(gestion + "/resultat").with(user(coupe.responsable())).with(csrf())
                .param("scoreEquipe1", "0").param("scoreEquipe2", "0");
        boolean rifEnPremier = m.equipe1().equals("Rif");
        ajouterTitulaires(score, "joueursEquipe1", rifEnPremier ? a : b);
        ajouterTitulaires(score, "joueursEquipe2", rifEnPremier ? b : a);
        String libre = rifEnPremier ? "joueursEquipe2" : "joueursEquipe1";
        score.param(libre + "[5].joueurId", "").param(libre + "[5].estTitulaire", "false").param(libre + "[5].buts", "0");
        mvc.perform(post(gestion + "/resultat").with(user(coupe.responsable())).with(csrf())
                        .param("scoreEquipe1", "2").param("scoreEquipe2", "1"))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("resultatForm"))
                .andExpect(content().string(containsString("titulaires")));
        assertThat(consultation.detailMatch(m.id()).match().statut()).isEqualTo(StatutMatch.PLANIFIE);
        mvc.perform(score).andExpect(redirectedUrl(consultationMatch));
        assertThat(consultation.detailMatch(m.id()).equipe1()).hasSize(5);
        assertThat(consultation.detailMatch(m.id()).equipe2()).hasSize(5);
        mvc.perform(get(gestion + "/resultat").with(user(coupe.responsable()))).andExpect(status().isOk());
    }

    private void ajouterTitulaires(MockHttpServletRequestBuilder requete, String liste, List<Joueur> equipe) {
        for (int i = 0; i < 5; i++) {
            requete.param(liste + "[" + i + "].joueurId", equipe.get(i).getId().toString());
            requete.param(liste + "[" + i + "].estTitulaire", "true");
        }
    }

    @Test
    void forfaitSimplePuisDoubleRecalculeLesPointsMemeAvecUnScoreConventionnelDeZero() throws Exception {
        Coupe coupe = creerCoupe("Forfaits");
        List<Joueur> a = ajouterEquipe(coupe, "Ouest");
        List<Joueur> b = ajouterEquipe(coupe, "Centre");
        transactions.executeWithoutResult(s -> em.find(Tournoi.class, coupe.id()).setScoreForfait(0));
        Long id = tirer(coupe).id();
        Long org = coupe.responsable().getId();
        matchs.declarerForfaitParOrganisateur(org, id, a.getFirst().getEquipe().getId(), "Absence");
        var groupe = consultation.classements(coupe.id()).getFirst();
        assertThat(groupe.lignes().getFirst().equipe()).isEqualTo("Centre");
        assertThat(groupe.lignes().getFirst().points()).isEqualTo(3);
        assertThatThrownBy(() -> matchs.enregistrerResultat(org, id, new ResultatForm())).hasMessageContaining("forfait");
        mvc.perform(post("/joueur/matchs/{id}/forfait", id).with(user(CompteUserDetails.de(b.getFirst())))
                        .with(csrf()).param("motif", "Équipe également absente"))
                .andExpect(flash().attributeExists("succes"));
        assertThat(consultation.classements(coupe.id()).getFirst().lignes()).allMatch(l -> l.points() == 0);
    }

    @Test
    void egaliteALaDernierePlaceQualificativeSeResoutSurLaPageOrganisateur() throws Exception {
        Coupe coupe = creerCoupe("Départage");
        ajouterEquipe(coupe, "Aurore");
        ajouterEquipe(coupe, "Midi");
        ajouterEquipe(coupe, "Soir");
        mvc.perform(post(coupe.gestion() + "/tirage").with(user(coupe.responsable())).with(csrf()))
                .andExpect(flash().attributeExists("succes"));
        for (MatchVue m : consultation.calendrier(coupe.id())) {
            var detail = consultation.detailMatch(m.id());
            matchs.declarerForfaitParOrganisateur(coupe.responsable().getId(), m.id(), detail.equipe1Id(), "Absence");
            matchs.declarerForfaitParOrganisateur(coupe.responsable().getId(), m.id(), detail.equipe2Id(), "Absence");
        }
        String departage = coupe.gestion() + "/groupes/" + consultation.classements(coupe.id()).getFirst().id() + "/departager";
        mvc.perform(get(coupe.gestion()).with(user(coupe.responsable())))
                .andExpect(content().string(containsString(departage)))
                .andExpect(content().string(not(containsString("Générer la phase éliminatoire"))));
        mvc.perform(post(departage).with(user(coupe.responsable())).with(csrf())).andExpect(flash().attributeExists("succes"));
        mvc.perform(get(coupe.gestion()).with(user(coupe.responsable())))
                .andExpect(content().string(containsString("Générer la phase éliminatoire")));
    }

    @Test
    void logosPartagesRefusesALaCreationEtALaModification() {
        Coupe coupe = creerCoupe("Logos");
        List<Joueur> a = ajouterEquipe(coupe, "Bleus");
        List<Joueur> b = ajouterEquipe(coupe, "Verts");
        equipes.modifierLogo(a.getFirst().getId(), "  image.png  ");
        assertThatThrownBy(() -> equipes.modifierLogo(b.getFirst().getId(), "IMAGE.png"))
                .isInstanceOf(RegleMetierException.class).hasMessageContaining("logo");
        Long joueurLibre = transactions.execute(s -> {
            Joueur j = new Joueur();
            renseignerCompte(j, "Libre", "Test");
            em.persist(j);
            return j.getId();
        });
        EquipeForm f = new EquipeForm();
        f.setNom("Rouges");
        f.setClubId(a.getFirst().getEquipe().getClub().getId());
        f.setLogo("image.png");
        assertThatThrownBy(() -> equipes.creerEquipe(joueurLibre, f)).isInstanceOf(RegleMetierException.class);
        f.setLogo(" ");
        equipes.creerEquipe(joueurLibre, f);
        equipes.modifierLogo(a.getFirst().getId(), "");
        equipes.modifierLogo(b.getFirst().getId(), "image.png");
        assertThat(consultation.monEquipe(b.getFirst().getId()).orElseThrow().logo()).isEqualTo("image.png");
    }

    @Test
    void repriseDesAnciensLogosConserveLePremierEtSupporteUnSecondDemarrage() throws Exception {
        Coupe coupe = creerCoupe("Reprise");
        List<Joueur> premiere = ajouterEquipe(coupe, "Première");
        List<Joueur> suivante = ajouterEquipe(coupe, "Suivante");
        equipes.modifierLogo(suivante.getFirst().getId(), "blason.png");
        transactions.executeWithoutResult(s -> em.createNativeQuery("update equipe set logo = :logo, logo_cle = null where id = :id")
                .setParameter("logo", "  Blason.PNG  ").setParameter("id", premiere.getFirst().getEquipe().getId()).executeUpdate());
        equipes.regulariserAnciensLogos();
        equipes.regulariserAnciensLogos();
        assertThat(consultation.monEquipe(premiere.getFirst().getId()).orElseThrow().logo()).isEqualTo("Blason.PNG");
        var aCorriger = consultation.monEquipe(suivante.getFirst().getId()).orElseThrow();
        assertThat(aCorriger.logo()).isNull();
        assertThat(aCorriger.logoPartage()).isTrue();
        CompteUserDetails capitaine = CompteUserDetails.de(suivante.getFirst());
        mvc.perform(get("/joueur/equipe").with(user(capitaine))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Le capitaine doit choisir un nouveau logo")));
        mvc.perform(post("/joueur/equipe/logo").with(user(capitaine)).with(csrf()).param("logo", "nouveau-blason.png"))
                .andExpect(flash().attributeExists("succes"));
        assertThat(consultation.monEquipe(capitaine.getId()).orElseThrow().logoPartage()).isFalse();
    }

    @Test
    void jeudiAttendMercrediEtUnScoreFuturEstRefuse() throws Exception {
        Coupe coupe = creerCoupe("Ordre");
        ajouterEquipe(coupe, "Nord");
        ajouterEquipe(coupe, "Sud");
        ajouterEquipe(coupe, "Est");
        mvc.perform(post(coupe.gestion() + "/tirage").with(user(coupe.responsable())).with(csrf())).andExpect(flash().attributeExists("succes"));
        List<MatchVue> calendrier = consultation.calendrier(coupe.id());
        LocalDate mercredi = LocalDate.now().minusWeeks(2).with(java.time.DayOfWeek.WEDNESDAY);
        transactions.executeWithoutResult(s -> {
            em.find(Match.class, calendrier.get(0).id()).setDateHeure(mercredi.atTime(12, 30));
            em.find(Match.class, calendrier.get(1).id()).setDateHeure(mercredi.plusDays(1).atTime(12, 30));
            em.find(Match.class, calendrier.get(2).id()).setDateHeure(LocalDate.now().plusDays(2).atTime(12, 0));
        });
        Long id = coupe.responsable().getId();
        assertThatThrownBy(() -> matchs.enregistrerResultat(id, calendrier.get(1).id(), new ResultatForm()))
                .isInstanceOf(RegleMetierException.class).hasMessageContaining("match n° 1");
        for (int i = 0; i < 2; i++) {
            Long match = calendrier.get(i).id();
            assertThat(matchs.actions(id, true, match).blocageResultat()).isNull();
            matchs.declarerForfaitParOrganisateur(id, match, consultation.detailMatch(match).equipe1Id(), "Absent");
        }
        assertThatThrownBy(() -> matchs.enregistrerResultat(id, calendrier.get(2).id(), new ResultatForm()))
                .isInstanceOf(RegleMetierException.class).hasMessageContaining("à partir du");
        mvc.perform(get("/consultation/matchs/{id}", calendrier.get(2).id()).with(user(coupe.responsable())))
                .andExpect(content().string(containsString("Saisie du résultat à partir du")))
                .andExpect(content().string(not(containsString("Saisir le résultat et les participants"))));
    }

    private List<JoueurStatForm> feuilleDeFinale(List<Joueur> effectif) {
        List<JoueurStatForm> feuille = new ArrayList<>();
        for (Joueur joueur : effectif) {
            JoueurStatForm ligne = new JoueurStatForm();
            ligne.setJoueurId(joueur.getId());
            feuille.add(ligne);
        }
        feuille.getFirst().setMinuteSortie(15);
        feuille.getLast().setEstTitulaire(false);
        feuille.getLast().setMinuteEntree(15);
        feuille.getLast().setButs(1);
        return feuille;
    }

    private MatchVue tirer(Coupe coupe) throws Exception {
        mvc.perform(post(coupe.gestion() + "/tirage").with(user(coupe.responsable())).with(csrf()))
                .andExpect(redirectedUrl(coupe.gestion())).andExpect(flash().attributeExists("succes"));
        assertThat(consultation.tournoi(coupe.id()).statut()).isEqualTo(StatutTournoi.TIRAGE_EFFECTUE);
        assertThat(consultation.calendrier(coupe.id())).hasSize(1);
        return consultation.calendrier(coupe.id()).getFirst();
    }

    // Les requêtes MVC suivantes s'exécutent après validation de la transaction des données de test.
    private Coupe creerCoupe(String nom) {
        return transactions.execute(status -> {
            Organisateur responsable = new Organisateur();
            renseignerCompte(responsable, nom, "Responsable");
            em.persist(responsable);
            Tournoi tournoi = new Tournoi();
            tournoi.setOrganisateur(responsable);
            tournoi.setNom(nom);
            tournoi.setSport("Football à 5");
            tournoi.setDateDebut(LocalDate.now(java.time.ZoneId.of("Africa/Casablanca")).minusDays(10));
            tournoi.setDateFin(tournoi.getDateDebut().plusDays(4));
            tournoi.setNbGroupes(1);
            tournoi.setNbQualifiesParGroupe(2);
            tournoi.setStatut(StatutTournoi.INSCRIPTION_CLOSE);
            em.persist(tournoi);
            return new Coupe(tournoi.getId(), CompteUserDetails.de(responsable));
        });
    }

    private List<Joueur> ajouterEquipe(Coupe coupe, String nom) {
        return transactions.execute(status -> {
            Club club = new Club();
            club.setNom("Club " + nom);
            em.persist(club);
            Equipe equipe = new Equipe();
            equipe.setNom(nom);
            equipe.setClub(club);
            em.persist(equipe);
            Inscription inscription = new Inscription();
            inscription.setTournoi(em.getReference(Tournoi.class, coupe.id()));
            inscription.setEquipe(equipe);
            inscription.setStatut(StatutInscription.VALIDEE);
            em.persist(inscription);
            List<Joueur> effectif = new ArrayList<>();
            for (int numero = 1; numero <= 6; numero++) {
                Joueur j = new Joueur();
                renseignerCompte(j, nom, "J" + numero);
                j.setNumeroMaillot(numero);
                j.setEstCapitaine(numero == 1);
                j.setEquipe(equipe);
                em.persist(j);
                effectif.add(j);
            }
            return effectif;
        });
    }

    private void renseignerCompte(Utilisateur compte, String nom, String prenom) {
        compte.setNom(nom);
        compte.setPrenom(prenom);
        compte.setEmail(prenom + "." + nom + "@example.test");
        compte.setMotDePasse("inutilisé-par-mockmvc");
    }
}
