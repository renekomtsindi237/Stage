package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.CollecteRequest;
import cm.imf.pipeline.dto.request.SyncRequest;
import cm.imf.pipeline.dto.response.SyncItemResult;
import cm.imf.pipeline.dto.response.SyncResponse;
import cm.imf.pipeline.dto.response.SyncStatusResponse;
import cm.imf.pipeline.entity.CollecteTerrain;
import cm.imf.pipeline.entity.SyncLog;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.CanalPaiement;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.enums.StatutCollecte;
import cm.imf.pipeline.repository.CollecteRepository;
import cm.imf.pipeline.repository.SyncLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollecteSyncService — tests unitaires (offline→online sync)")
class CollecteSyncServiceTest {

    @Mock CollecteRepository      collecteRepository;
    @Mock SyncLogRepository       syncLogRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks CollecteSyncService syncService;

    private User agent;
    private String syncId;

    @BeforeEach
    void setUp() {
        agent = User.builder()
                .id(1L).username("agent01").role(Role.AGENT).actif(true).build();
        syncId = UUID.randomUUID().toString();
    }

    private CollecteRequest validItem(String mobileId) {
        return new CollecteRequest(mobileId, "CLI001", "PRE001", LocalDate.now(),
                new BigDecimal("25000"), CanalPaiement.MTN, "REF-" + mobileId,
                null, null, null);
    }

    private SyncRequest validRequest(List<CollecteRequest> items) {
        return new SyncRequest(syncId, "DEVICE-001",
                OffsetDateTime.now(), items);
    }

    // ── Cas nominal ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("processSync — batch valide → tous CONFIRMEE + message succès")
    void processSync_batch_valide_tous_confirmes() {
        when(syncLogRepository.existsBySyncId(syncId)).thenReturn(false);
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(collecteRepository.existsByIdCollecteMobile(any())).thenReturn(false);
        when(collecteRepository.existsByReferenceTransactionAndDateCollecte(any(), any())).thenReturn(false);
        when(collecteRepository.save(any())).thenAnswer(inv -> {
            CollecteTerrain c = inv.getArgument(0);
            c.setId(42L);
            return c;
        });

        SyncResponse result = syncService.processSync(
                validRequest(List.of(validItem("MOB-001"), validItem("MOB-002"))),
                agent, "192.168.1.5");

        assertThat(result.syncId()).isEqualTo(syncId);
        assertThat(result.statutGlobal()).isEqualTo(SyncResponse.StatutGlobal.COMPLETE);
        assertThat(result.stats().succes()).isEqualTo(2);
        assertThat(result.stats().total()).isEqualTo(2);
        assertThat(result.stats().conflits()).isEqualTo(0);
        assertThat(result.messageResume()).contains("succès");
        assertThat(result.resultats()).allMatch(r -> r.code().equals(SyncItemResult.CODE_SUCCESS));
        verify(eventPublisher).publishEvent(any());
    }

    // ── Idempotence ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("processSync — syncId déjà traité → réponse idempotente sans double insertion")
    void processSync_idempotent_second_appel() {
        SyncLog existingLog = SyncLog.builder()
                .syncId(syncId).deviceId("DEVICE-001").agent(agent)
                .nbItemsSoumis(2).nbSucces(2).statutSync("COMPLETE")
                .syncCompletedAt(OffsetDateTime.now().minusMinutes(5))
                .build();

        when(syncLogRepository.existsBySyncId(syncId)).thenReturn(true);
        when(syncLogRepository.findBySyncId(syncId)).thenReturn(Optional.of(existingLog));

        SyncResponse result = syncService.processSync(
                validRequest(List.of(validItem("MOB-001"))),
                agent, "192.168.1.5");

        assertThat(result.statutGlobal()).isEqualTo(SyncResponse.StatutGlobal.COMPLETE);
        assertThat(result.messageResume()).contains("déjà traitée");
        // Aucune insertion en DB
        verify(collecteRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── Doublons ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("processSync — item ID mobile déjà connu → DOUBLON avec message explicite")
    void processSync_item_doublon_id_mobile() {
        when(syncLogRepository.existsBySyncId(syncId)).thenReturn(false);
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(collecteRepository.existsByIdCollecteMobile("MOB-EXIST")).thenReturn(true);
        when(collecteRepository.findByIdCollecteMobile("MOB-EXIST")).thenReturn(
                Optional.of(CollecteTerrain.builder().id(99L)
                        .statut(StatutCollecte.CONFIRMEE).build()));

        SyncResponse result = syncService.processSync(
                validRequest(List.of(validItem("MOB-EXIST"))),
                agent, "192.168.1.5");

        assertThat(result.resultats()).hasSize(1);
        SyncItemResult item = result.resultats().get(0);
        assertThat(item.code()).isEqualTo(SyncItemResult.CODE_DOUBLON);
        assertThat(item.messageUtilisateur()).contains("Doublon");
        assertThat(item.idServeur()).isEqualTo(99L);
    }

    @Test
    @DisplayName("processSync — item référence transaction dupliquée → CONFLIT")
    void processSync_item_doublon_reference_transaction() {
        when(syncLogRepository.existsBySyncId(syncId)).thenReturn(false);
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(collecteRepository.existsByIdCollecteMobile(any())).thenReturn(false);
        when(collecteRepository.existsByReferenceTransactionAndDateCollecte(
                eq("REF-MOB-001"), any())).thenReturn(true);

        SyncResponse result = syncService.processSync(
                validRequest(List.of(validItem("MOB-001"))),
                agent, "192.168.1.5");

        SyncItemResult item = result.resultats().get(0);
        assertThat(item.code()).isEqualTo(SyncItemResult.CODE_CONFLIT);
        assertThat(item.messageUtilisateur()).contains("référence de transaction");
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("processSync — item date future → CONFLIT avec message validation")
    void processSync_item_date_future() {
        CollecteRequest futurItem = new CollecteRequest(
                "MOB-FUTURE", "CLI001", "PRE001",
                LocalDate.now().plusDays(1), // date future
                new BigDecimal("25000"), CanalPaiement.MTN,
                "REF-FUTURE", null, null, null);

        when(syncLogRepository.existsBySyncId(syncId)).thenReturn(false);
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(collecteRepository.existsByIdCollecteMobile("MOB-FUTURE")).thenReturn(false);

        SyncResponse result = syncService.processSync(
                validRequest(List.of(futurItem)), agent, "192.168.1.5");

        assertThat(result.resultats().get(0).code()).isEqualTo(SyncItemResult.CODE_CONFLIT);
        assertThat(result.resultats().get(0).messageUtilisateur())
                .contains("futur");
    }

    @Test
    @DisplayName("processSync — montant négatif → CONFLIT avec message validation")
    void processSync_item_montant_negatif() {
        CollecteRequest badAmount = new CollecteRequest(
                "MOB-NEG", "CLI001", "PRE001", LocalDate.now(),
                new BigDecimal("-500"), CanalPaiement.MTN, null, null, null, null);

        when(syncLogRepository.existsBySyncId(syncId)).thenReturn(false);
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(collecteRepository.existsByIdCollecteMobile("MOB-NEG")).thenReturn(false);

        SyncResponse result = syncService.processSync(
                validRequest(List.of(badAmount)), agent, "192.168.1.5");

        assertThat(result.resultats().get(0).code()).isEqualTo(SyncItemResult.CODE_CONFLIT);
        assertThat(result.resultats().get(0).messageUtilisateur())
                .contains("zéro");
    }

    // ── Statut partiel ────────────────────────────────────────────────────────

    @Test
    @DisplayName("processSync — 1 succès + 1 conflit → PARTIELLE avec stats correctes")
    void processSync_statut_partielle() {
        when(syncLogRepository.existsBySyncId(syncId)).thenReturn(false);
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // MOB-OK : valide
        when(collecteRepository.existsByIdCollecteMobile("MOB-OK")).thenReturn(false);
        when(collecteRepository.existsByReferenceTransactionAndDateCollecte(
                eq("REF-MOB-OK"), any())).thenReturn(false);
        when(collecteRepository.save(any())).thenAnswer(inv -> {
            CollecteTerrain c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        // MOB-DUP : doublon ID
        when(collecteRepository.existsByIdCollecteMobile("MOB-DUP")).thenReturn(true);
        when(collecteRepository.findByIdCollecteMobile("MOB-DUP")).thenReturn(
                Optional.of(CollecteTerrain.builder().id(55L).statut(StatutCollecte.CONFIRMEE).build()));

        SyncResponse result = syncService.processSync(
                validRequest(List.of(validItem("MOB-OK"), validItem("MOB-DUP"))),
                agent, "192.168.1.5");

        assertThat(result.stats().total()).isEqualTo(2);
        assertThat(result.stats().succes()).isEqualTo(1);
        assertThat(result.stats().doublons()).isEqualTo(1);
        // COMPLETE car doublons ne sont pas des échecs critiques
        assertThat(result.statutGlobal()).isEqualTo(SyncResponse.StatutGlobal.COMPLETE);
    }

    // ── SyncLog sauvegardé avec bonnes stats ──────────────────────────────────

    @Test
    @DisplayName("processSync — SyncLog sauvegardé avec les statistiques correctes")
    void processSync_synclog_sauvegarde_avec_stats() {
        when(syncLogRepository.existsBySyncId(syncId)).thenReturn(false);
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(collecteRepository.existsByIdCollecteMobile(any())).thenReturn(false);
        when(collecteRepository.existsByReferenceTransactionAndDateCollecte(any(), any())).thenReturn(false);
        when(collecteRepository.save(any())).thenAnswer(inv -> {
            CollecteTerrain c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        syncService.processSync(
                validRequest(List.of(validItem("MOB-001"))),
                agent, "192.168.1.5");

        ArgumentCaptor<SyncLog> captor = ArgumentCaptor.forClass(SyncLog.class);
        verify(syncLogRepository, times(2)).save(captor.capture());

        SyncLog finalLog = captor.getValue();
        assertThat(finalLog.getNbSucces()).isEqualTo(1);
        assertThat(finalLog.getNbErreurs()).isEqualTo(0);
        assertThat(finalLog.getStatutSync()).isEqualTo("COMPLETE");
        assertThat(finalLog.getSyncCompletedAt()).isNotNull();
        assertThat(finalLog.getMessageSync()).isNotNull();
    }

    // ── getSyncStatus ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getSyncStatus — aucune sync → message explicite")
    void getSyncStatus_aucune_sync() {
        when(syncLogRepository.findByDeviceIdOrderBySyncStartedAtDesc("DEVICE-999"))
                .thenReturn(List.of());

        SyncStatusResponse status = syncService.getSyncStatus("DEVICE-999");

        assertThat(status.nbSyncTotal()).isEqualTo(0);
        assertThat(status.message()).contains("Aucune synchronisation");
    }

    @Test
    @DisplayName("getSyncStatus — conflits ouverts → message d'alerte")
    void getSyncStatus_conflits_ouverts() {
        SyncLog log = SyncLog.builder()
                .syncId("S1").deviceId("DEVICE-001").agent(agent)
                .nbConflits(3).statutSync("PARTIELLE")
                .syncCompletedAt(OffsetDateTime.now().minusHours(1))
                .build();

        when(syncLogRepository.findByDeviceIdOrderBySyncStartedAtDesc("DEVICE-001"))
                .thenReturn(List.of(log));
        when(syncLogRepository.sumSuccesByDeviceId("DEVICE-001")).thenReturn(10);
        when(syncLogRepository.sumConflitsOuvertsByDeviceId("DEVICE-001")).thenReturn(3);

        SyncStatusResponse status = syncService.getSyncStatus("DEVICE-001");

        assertThat(status.nbConflitsOuverts()).isEqualTo(3);
        assertThat(status.message()).contains("conflit");
    }
}
