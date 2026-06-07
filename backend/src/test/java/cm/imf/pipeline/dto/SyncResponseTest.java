package cm.imf.pipeline.dto;

import cm.imf.pipeline.dto.response.SyncItemResult;
import cm.imf.pipeline.dto.response.SyncResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SyncResponse — tests du modèle et calculs de statistiques")
class SyncResponseTest {

    @Test
    @DisplayName("SyncStats.compute — compte correctement par catégorie")
    void syncStats_compute_correct() {
        List<SyncItemResult> items = List.of(
                SyncItemResult.succes("M1", 1L, "ok"),
                SyncItemResult.succes("M2", 2L, "ok"),
                SyncItemResult.doublon("M3", 3L, "doublon"),
                SyncItemResult.conflit("M4", "conflit"),
                SyncItemResult.erreur("M5", "erreur"),
                SyncItemResult.enAttente("M6", 6L, "en attente")
        );

        SyncResponse.SyncStats stats = SyncResponse.SyncStats.compute(items);

        assertThat(stats.total()).isEqualTo(6);
        assertThat(stats.succes()).isEqualTo(2);
        assertThat(stats.doublons()).isEqualTo(1);
        assertThat(stats.conflits()).isEqualTo(1);
        assertThat(stats.erreurs()).isEqualTo(1);
        assertThat(stats.enAttente()).isEqualTo(1);
    }

    @Test
    @DisplayName("SyncResponse.of — COMPLETE quand aucun conflit ni erreur")
    void syncResponse_statut_complete() {
        List<SyncItemResult> items = List.of(
                SyncItemResult.succes("M1", 1L, "ok"),
                SyncItemResult.doublon("M2", 2L, "doublon"));
        SyncResponse response = SyncResponse.of("sync-id", items, "OK");

        assertThat(response.statutGlobal()).isEqualTo(SyncResponse.StatutGlobal.COMPLETE);
    }

    @Test
    @DisplayName("SyncResponse.of — PARTIELLE quand certains items ont échoué")
    void syncResponse_statut_partielle() {
        List<SyncItemResult> items = List.of(
                SyncItemResult.succes("M1", 1L, "ok"),
                SyncItemResult.conflit("M2", "conflit"));
        SyncResponse response = SyncResponse.of("sync-id", items, "Partielle");

        assertThat(response.statutGlobal()).isEqualTo(SyncResponse.StatutGlobal.PARTIELLE);
    }

    @Test
    @DisplayName("SyncResponse.of — ECHEC quand tous les items ont échoué")
    void syncResponse_statut_echec() {
        List<SyncItemResult> items = List.of(
                SyncItemResult.conflit("M1", "conflit 1"),
                SyncItemResult.erreur("M2", "erreur 2"));
        SyncResponse response = SyncResponse.of("sync-id", items, "Echec");

        assertThat(response.statutGlobal()).isEqualTo(SyncResponse.StatutGlobal.ECHEC);
    }

    @Test
    @DisplayName("SyncItemResult.isSuccess — true pour SUCCESS et DOUBLON")
    void syncItemResult_isSuccess() {
        assertThat(SyncItemResult.succes("M1", 1L, "ok").isSuccess()).isTrue();
        assertThat(SyncItemResult.doublon("M1", 1L, "ok").isSuccess()).isTrue();
        assertThat(SyncItemResult.conflit("M1", "conflit").isSuccess()).isFalse();
        assertThat(SyncItemResult.erreur("M1", "erreur").isSuccess()).isFalse();
        assertThat(SyncItemResult.enAttente("M1", 1L, "attente").isSuccess()).isFalse();
    }

    @Test
    @DisplayName("SyncResponse — syncId et processedAt sont présents")
    void syncResponse_champs_obligatoires() {
        SyncResponse response = SyncResponse.of("my-sync-id", List.of(
                SyncItemResult.succes("M1", 1L, "ok")), "message");

        assertThat(response.syncId()).isEqualTo("my-sync-id");
        assertThat(response.processedAt()).isNotNull();
        assertThat(response.messageResume()).isEqualTo("message");
    }
}
