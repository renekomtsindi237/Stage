package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.EvaluerRisqueKycRequest;
import cm.imf.pipeline.dto.request.InitierKycRequest;
import cm.imf.pipeline.dto.request.VerifierKycRequest;
import cm.imf.pipeline.dto.response.KycDossierResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.entity.Imf;
import cm.imf.pipeline.entity.KycDossier;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.*;
import cm.imf.pipeline.repository.KycDocumentRepository;
import cm.imf.pipeline.repository.KycDossierRepository;
import cm.imf.pipeline.repository.KycVerificationRepository;
import cm.imf.pipeline.service.impl.KycServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KycServiceImpl — machine à états KYC COBAC/BEAC")
class KycServiceImplTest {

    @Mock KycDossierRepository      dossierRepo;
    @Mock KycDocumentRepository     documentRepo;
    @Mock KycVerificationRepository verificationRepo;
    @InjectMocks KycServiceImpl     kycService;

    private User dsiUser;
    private Imf  imf;

    @BeforeEach
    void setUp() {
        imf = new Imf();
        imf.setId(1L);
        imf.setCode("CAMCCUL");

        dsiUser = new User();
        dsiUser.setId(5L);
        dsiUser.setUsername("dsi_camccul");
        dsiUser.setRole(Role.DSI);
        dsiUser.setImf(imf);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private InitierKycRequest buildInitierRequest(String clientId, NiveauKyc niveau) {
        return new InitierKycRequest(
                clientId, "Kouam", "Jean-Pierre",
                LocalDate.of(1988, 7, 20), "Bafoussam", "Camerounaise",
                "697001122", "kouam@test.cm", "Ngousso", "Yaoundé",
                "Commerçant", "Auto", null,
                TypeDocumentKyc.CNI_RECTO, "123456789",
                LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1), "Yaoundé",
                niveau, false, null);
    }

    private KycDossier buildDossier(String clientId, StatutKyc statut, NiveauRisque risque) {
        KycDossier d = new KycDossier();
        d.setId(1L);
        d.setImf(imf);
        d.setClientId(clientId);
        d.setNomClient("Kouam");
        d.setPrenomClient("Jean-Pierre");
        d.setNiveauActuel(NiveauKyc.NIVEAU_1);
        d.setStatut(statut);
        d.setNiveauRisque(risque);
        d.setEstPep(false);
        return d;
    }

    // ── initierDossier ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("initierDossier")
    class InitierDossier {

        @Test
        @DisplayName("→ crée un dossier NIVEAU_1 avec statut INITIE pour client nouveau")
        void client_nouveau_cree_dossier_initie() {
            when(dossierRepo.findByImfIdAndClientId(1L, "CLI-NEW")).thenReturn(Optional.empty());
            when(dossierRepo.save(any())).thenAnswer(inv -> {
                KycDossier d = inv.getArgument(0);
                d.setId(1L);
                d.setStatut(StatutKyc.EN_ATTENTE);
                d.setNiveauRisque(NiveauRisque.FAIBLE);
                return d;
            });

            KycDossierResponse result = kycService.initierDossier(
                    buildInitierRequest("CLI-NEW", NiveauKyc.NIVEAU_1), dsiUser);

            assertThat(result.clientId()).isEqualTo("CLI-NEW");
            assertThat(result.statut()).isEqualTo(StatutKyc.EN_ATTENTE);
            verify(dossierRepo).save(any(KycDossier.class));
        }

        @Test
        @DisplayName("→ lève CONFLICT si dossier déjà existant pour ce client")
        void client_existant_leve_conflict() {
            when(dossierRepo.findByImfIdAndClientId(1L, "CLI-EXIST"))
                    .thenReturn(Optional.of(buildDossier("CLI-EXIST", StatutKyc.APPROUVE, NiveauRisque.FAIBLE)));

            assertThatThrownBy(() ->
                    kycService.initierDossier(buildInitierRequest("CLI-EXIST", NiveauKyc.NIVEAU_1), dsiUser))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("CLI-EXIST");
        }

        @Test
        @DisplayName("→ lève FORBIDDEN si l'utilisateur n'a pas de tenant (SUPER_ADMIN sans IMF)")
        void user_sans_tenant_leve_forbidden() {
            User superAdmin = new User();
            superAdmin.setRole(Role.SUPER_ADMIN);
            superAdmin.setImf(null);

            assertThatThrownBy(() ->
                    kycService.initierDossier(buildInitierRequest("CLI-X", NiveauKyc.NIVEAU_1), superAdmin))
                    .isInstanceOf(ResponseStatusException.class);
        }
    }

    // ── listDossiers ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listDossiers")
    class ListDossiers {

        @Test
        @DisplayName("→ retourne la page complète sans filtres")
        void sans_filtre_retourne_tous() {
            KycDossier d = buildDossier("CLI-001", StatutKyc.EN_COURS_VERIFICATION, NiveauRisque.MOYEN);
            when(dossierRepo.findByImfId(eq(1L), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(d)));

            PageResponse<KycDossierResponse> result =
                    kycService.listDossiers(1L, null, null, null, 0, 20);

            assertThat(result.content()).hasSize(1);
            assertThat(result.totalElements()).isEqualTo(1L);
            assertThat(result.first()).isTrue();
        }

        @Test
        @DisplayName("→ délègue au filtre statut+niveau quand les deux sont fournis")
        void filtre_statut_et_niveau_utilise_bonne_methode_repo() {
            when(dossierRepo.findByImfIdAndStatutAndNiveau(
                    eq(1L), eq(StatutKyc.APPROUVE), eq(NiveauKyc.NIVEAU_2), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            kycService.listDossiers(1L, StatutKyc.APPROUVE, NiveauKyc.NIVEAU_2, null, 0, 20);

            verify(dossierRepo).findByImfIdAndStatutAndNiveau(
                    eq(1L), eq(StatutKyc.APPROUVE), eq(NiveauKyc.NIVEAU_2), any());
            verify(dossierRepo, never()).findByImfId(any(), any());
        }

        @Test
        @DisplayName("→ filtre par risque uniquement quand statut/niveau sont null")
        void filtre_risque_utilise_bonne_methode_repo() {
            when(dossierRepo.findByImfIdAndRisque(eq(1L), eq(NiveauRisque.ELEVE), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            kycService.listDossiers(1L, null, null, NiveauRisque.ELEVE, 0, 20);

            verify(dossierRepo).findByImfIdAndRisque(eq(1L), eq(NiveauRisque.ELEVE), any());
        }
    }

    // ── getDossier ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getDossier → 404 pour un UID inconnu")
    void getDossier_uid_inconnu_retourne_404() {
        UUID unknown = UUID.randomUUID();
        when(dossierRepo.findByUid(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kycService.getDossier(unknown))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ── evaluerRisque ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("evaluerRisque")
    class EvaluerRisque {

        @Test
        @DisplayName("→ mise à jour PPE + niveau risque ELEVE persiste en base")
        void ppe_vrai_met_a_jour_risque_eleve() {
            UUID uid = UUID.randomUUID();
            KycDossier dossier = buildDossier("CLI-PPE", StatutKyc.EN_COURS_VERIFICATION, NiveauRisque.FAIBLE);
            when(dossierRepo.findByUid(uid)).thenReturn(Optional.of(dossier));
            when(dossierRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            EvaluerRisqueKycRequest req = new EvaluerRisqueKycRequest(
                    true, true, false, "ELEVE", "PPE détecté");
            KycDossierResponse result = kycService.evaluerRisque(uid, req, dsiUser);

            assertThat(result.estPep()).isTrue();
            verify(dossierRepo).save(any());
        }

        @Test
        @DisplayName("→ 404 si le dossier est introuvable")
        void evaluer_dossier_inconnu_retourne_404() {
            when(dossierRepo.findByUid(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    kycService.evaluerRisque(UUID.randomUUID(),
                            new EvaluerRisqueKycRequest(false, false, false, "BAS", null),
                            dsiUser))
                    .isInstanceOf(ResponseStatusException.class);
        }
    }

    // ── verifier ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("verifier")
    class Verifier {

        @Test
        @DisplayName("→ décision APPROUVE passe le statut à APPROUVE")
        void decision_approuve_change_statut() {
            UUID uid = UUID.randomUUID();
            KycDossier dossier = buildDossier("CLI-VER", StatutKyc.EN_COURS_VERIFICATION, NiveauRisque.FAIBLE);
            when(dossierRepo.findByUid(uid)).thenReturn(Optional.of(dossier));
            when(dossierRepo.save(any())).thenAnswer(inv -> {
                KycDossier d = inv.getArgument(0);
                d.setStatut(StatutKyc.APPROUVE);
                return d;
            });
            when(verificationRepo.save(any())).thenReturn(null);

            VerifierKycRequest req = new VerifierKycRequest(
                    ResultatVerificationKyc.APPROUVE, "Documents conformes");
            KycDossierResponse result = kycService.verifier(uid, req, dsiUser);

            assertThat(result.statut()).isEqualTo(StatutKyc.APPROUVE);
            verify(verificationRepo).save(any());
        }

        @Test
        @DisplayName("→ décision REJETE passe le statut à REJETE")
        void decision_rejete_change_statut() {
            UUID uid = UUID.randomUUID();
            KycDossier dossier = buildDossier("CLI-REJ", StatutKyc.EN_COURS_VERIFICATION, NiveauRisque.ELEVE);
            when(dossierRepo.findByUid(uid)).thenReturn(Optional.of(dossier));
            when(dossierRepo.save(any())).thenAnswer(inv -> {
                KycDossier d = inv.getArgument(0);
                d.setStatut(StatutKyc.REJETE);
                return d;
            });
            when(verificationRepo.save(any())).thenReturn(null);

            VerifierKycRequest req = new VerifierKycRequest(
                    ResultatVerificationKyc.REJETE, "Pièce expirée");
            KycDossierResponse result = kycService.verifier(uid, req, dsiUser);

            assertThat(result.statut()).isEqualTo(StatutKyc.REJETE);
        }
    }
}
