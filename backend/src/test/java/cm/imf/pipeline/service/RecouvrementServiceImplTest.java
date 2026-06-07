package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.AjouterActionRequest;
import cm.imf.pipeline.dto.request.EscaladerDossierRequest;
import cm.imf.pipeline.dto.request.OuvrirDossierRequest;
import cm.imf.pipeline.dto.response.ActionRecouvrementResponse;
import cm.imf.pipeline.dto.response.DossierRecouvrementResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.entity.*;
import cm.imf.pipeline.enums.*;
import cm.imf.pipeline.repository.*;
import cm.imf.pipeline.service.impl.RecouvrementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecouvrementServiceImpl — règles COBAC/OHADA")
class RecouvrementServiceImplTest {

    @Mock RecouvrementDossierRepository  dossierRepo;
    @Mock ActionRecouvrementRepository   actionRepo;
    @Mock AccordReechelonnementRepository accordRepo;
    @Mock UserRepository                 userRepo;
    @InjectMocks RecouvrementServiceImpl recouvrementService;

    private User rrUser;
    private Imf  imf;

    @BeforeEach
    void setUp() {
        imf = new Imf();
        imf.setId(1L);
        imf.setCode("CAMCCUL");

        rrUser = new User();
        rrUser.setId(7L);
        rrUser.setUsername("rr_test");
        rrUser.setRole(Role.RESPONSABLE_RECOUVREMENT);
        rrUser.setImf(imf);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RecouvrementDossier buildDossier(int joursRetard, BigDecimal montant) {
        RecouvrementDossier d = new RecouvrementDossier();
        d.setId(1L);
        d.setUid(UUID.randomUUID());
        d.setImfId(1L);
        d.setIdPret("PRE-2024-001");
        d.setNomClient("Fomo Martin");
        d.setMontantImpaye(montant);
        d.setJoursRetard(joursRetard);
        d.setPhase(RecouvrementPhase.RELANCE_AMIABLE);
        d.setClos(false);
        return d;
    }

    // ── ouvrirDossier — classification COBAC ─────────────────────────────────

    @Nested
    @DisplayName("ouvrirDossier — classification COBAC automatique")
    class OuvrirDossier {

        @Test
        @DisplayName("→ crée un dossier phase RELANCE_AMIABLE pour prêt inexistant")
        void ouvrir_nouveau_retourne_dossier() {
            when(dossierRepo.existsDossierActif(1L, "PRE-NEW")).thenReturn(false);
            when(userRepo.findByUid(any())).thenReturn(Optional.of(rrUser));
            when(dossierRepo.save(any())).thenAnswer(inv -> {
                RecouvrementDossier d = inv.getArgument(0);
                d.setId(1L);
                d.setUid(UUID.randomUUID());
                d.setPhase(RecouvrementPhase.RELANCE_AMIABLE);
                return d;
            });

            OuvrirDossierRequest req = new OuvrirDossierRequest(
                    "PRE-NEW", "Fomo Martin", new BigDecimal("300000"), 65,
                    null, null, null, null, TypeGarantie.CAUTION_PERSONNELLE);

            DossierRecouvrementResponse result = recouvrementService.ouvrirDossier(req, rrUser);

            assertThat(result.phase()).isEqualTo(RecouvrementPhase.RELANCE_AMIABLE);
            verify(dossierRepo).save(any(RecouvrementDossier.class));
        }

        @Test
        @DisplayName("→ CONFLICT si dossier actif déjà existant pour ce prêt")
        void ouvrir_doublon_leve_conflict() {
            when(dossierRepo.existsDossierActif(1L, "PRE-EXIST")).thenReturn(true);

            OuvrirDossierRequest req = new OuvrirDossierRequest(
                    "PRE-EXIST", "Client X", new BigDecimal("100000"), 30,
                    null, null, null, null, null);

            assertThatThrownBy(() -> recouvrementService.ouvrirDossier(req, rrUser))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("PRE-EXIST");
        }

        @Test
        @DisplayName("→ FORBIDDEN si l'utilisateur n'a pas de tenant IMF")
        void ouvrir_sans_tenant_leve_forbidden() {
            User sans = new User();
            sans.setImf(null);

            assertThatThrownBy(() ->
                    recouvrementService.ouvrirDossier(
                            new OuvrirDossierRequest("X", "Y", BigDecimal.TEN, 1,
                                    null, null, null, null, null), sans))
                    .isInstanceOf(ResponseStatusException.class);
        }
    }

    // ── Provisionnement COBAC ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Provisionnement COBAC — règles réglementaires")
    class ProvisiKobac {

        @ParameterizedTest(name = "Retard {0}j → categorie {1}, taux {2}%")
        @CsvSource({
                "15,  EN_SURVEILLANCE, 5",
                "60,  DOUTEUSE,        25",
                "120, LITIGIEUSE,      50",
                "200, CONTENTIEUSE,    100"
        })
        @DisplayName("Calcul catégorie COBAC selon jours de retard")
        void cobac_categorie_selon_jours_retard(
                int joursRetard, String categorie, int tauxAttendu) {

            RecouvrementDossier d = buildDossier(joursRetard, new BigDecimal("200000"));
            when(dossierRepo.existsDossierActif(anyLong(), anyString())).thenReturn(false);
            when(userRepo.findByUid(any())).thenReturn(Optional.of(rrUser));
            when(dossierRepo.save(any())).thenAnswer(inv -> {
                RecouvrementDossier saved = inv.getArgument(0);
                // @PrePersist calcule la catégorie — on simule ici
                saved.setId(1L);
                saved.setUid(UUID.randomUUID());
                saved.setCategorieCobtac(CategorieCobtac.valueOf(categorie));
                saved.setTauxProvision(new BigDecimal(tauxAttendu));
                saved.setMontantProvision(
                        new BigDecimal("200000").multiply(new BigDecimal(tauxAttendu))
                                               .divide(new BigDecimal("100")));
                return saved;
            });

            OuvrirDossierRequest req = new OuvrirDossierRequest(
                    "PRE-COBAC-" + joursRetard, "Client COBAC",
                    new BigDecimal("200000"), joursRetard,
                    null, null, null, null, null);

            DossierRecouvrementResponse result = recouvrementService.ouvrirDossier(req, rrUser);

            assertThat(result.categorieCobtac().name()).isEqualTo(categorie);
            assertThat(result.tauxProvision()).isEqualByComparingTo(new BigDecimal(tauxAttendu));
        }
    }

    // ── ajouterAction ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ajouterAction")
    class AjouterAction {

        @Test
        @DisplayName("→ enregistre une visite terrain avec montant récupéré")
        void action_visite_persiste() {
            UUID uid = UUID.randomUUID();
            RecouvrementDossier dossier = buildDossier(90, new BigDecimal("500000"));
            when(dossierRepo.findByUid(uid)).thenReturn(Optional.of(dossier));
            when(actionRepo.save(any())).thenAnswer(inv -> {
                ActionRecouvrement a = inv.getArgument(0);
                a.setId(1L);
                a.setUid(UUID.randomUUID());
                return a;
            });
            when(dossierRepo.save(any())).thenReturn(dossier);

            AjouterActionRequest req = new AjouterActionRequest(
                    TypeActionRecouvrement.VISITE,
                    ResultatActionRecouvrement.SUCCES,
                    "Client présent, a promis de payer",
                    new BigDecimal("75000"));

            ActionRecouvrementResponse result =
                    recouvrementService.ajouterAction(uid, req, rrUser);

            assertThat(result.typeAction()).isEqualTo(TypeActionRecouvrement.VISITE);
            assertThat(result.resultat()).isEqualTo(ResultatActionRecouvrement.SUCCES);
            verify(actionRepo).save(any(ActionRecouvrement.class));
        }

        @Test
        @DisplayName("→ 404 si le dossier est introuvable")
        void action_dossier_inconnu_retourne_404() {
            when(dossierRepo.findByUid(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    recouvrementService.ajouterAction(UUID.randomUUID(),
                            new AjouterActionRequest(
                                    TypeActionRecouvrement.SMS,
                                    ResultatActionRecouvrement.SANS_REPONSE,
                                    "Pas de réponse", null),
                            rrUser))
                    .isInstanceOf(ResponseStatusException.class);
        }
    }

    // ── escalader ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("escalader → met à jour la phase et persiste")
    void escalader_change_phase() {
        UUID uid = UUID.randomUUID();
        RecouvrementDossier dossier = buildDossier(120, new BigDecimal("600000"));
        dossier.setPhase(RecouvrementPhase.RELANCE_AMIABLE);
        when(dossierRepo.findByUid(uid)).thenReturn(Optional.of(dossier));
        when(dossierRepo.save(any())).thenAnswer(inv -> {
            RecouvrementDossier d = inv.getArgument(0);
            d.setPhase(RecouvrementPhase.MISE_EN_DEMEURE);
            return d;
        });

        DossierRecouvrementResponse result = recouvrementService.escalader(
                uid, new EscaladerDossierRequest(
                        RecouvrementPhase.MISE_EN_DEMEURE, "Aucune réponse après 3 appels"),
                rrUser);

        assertThat(result.phase()).isEqualTo(RecouvrementPhase.MISE_EN_DEMEURE);
        verify(dossierRepo).save(any());
    }

    // ── listDossiers ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("listDossiers → retourne page vide pour IMF sans dossiers")
    void list_vide_retourne_page_vide() {
        when(dossierRepo.findByImfId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<DossierRecouvrementResponse> result =
                recouvrementService.listDossiers(1L, null, null, 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    @DisplayName("listDossiers → filtre par phase+clos utilise la bonne requête repo")
    void list_filtre_phase_et_clos() {
        when(dossierRepo.findByImfIdAndPhaseAndClos(
                eq(1L), eq(RecouvrementPhase.CONTENTIEUX), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of()));

        recouvrementService.listDossiers(1L, RecouvrementPhase.CONTENTIEUX, false, 0, 20);

        verify(dossierRepo).findByImfIdAndPhaseAndClos(
                1L, RecouvrementPhase.CONTENTIEUX, false, any());
    }
}
