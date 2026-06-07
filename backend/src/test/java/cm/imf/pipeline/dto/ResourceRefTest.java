package cm.imf.pipeline.dto;

import cm.imf.pipeline.dto.response.ResourceRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests ResourceRef — cross-resource references canoniques.
 * Chaque référence expose uid, type et href vers /api/v1/{type}/{uid}.
 */
@DisplayName("ResourceRef — références croisées canoniques")
class ResourceRefTest {

    // ── ResourceRef.of() ──────────────────────────────────────────────────────

    @Test
    @DisplayName("of('clients', uid) → href='/api/v1/clients/{uid}'")
    void of_construit_href_correct() {
        String uid = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
        ResourceRef ref = ResourceRef.of("clients", uid);

        assertThat(ref).isNotNull();
        assertThat(ref.uid()).isEqualTo(uid);
        assertThat(ref.type()).isEqualTo("clients");
        assertThat(ref.href()).isEqualTo("/api/v1/clients/" + uid);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    @DisplayName("of avec uid null/vide/blank → retourne null (sûr)")
    void of_uid_null_ou_vide_retourne_null(String uid) {
        assertThat(ResourceRef.of("clients", uid)).isNull();
    }

    @Test
    @DisplayName("of avec type différent → href contient le bon type")
    void of_type_different() {
        ResourceRef ref = ResourceRef.of("prets", "abc-123");
        assertThat(ref.href()).startsWith("/api/v1/prets/");
        assertThat(ref.type()).isEqualTo("prets");
    }

    // ── ResourceRef.external() ────────────────────────────────────────────────

    @Test
    @DisplayName("external('prets', 'CBS-2024-001') → href correct")
    void external_construit_href() {
        ResourceRef ref = ResourceRef.external("prets", "CBS-2024-001");

        assertThat(ref).isNotNull();
        assertThat(ref.uid()).isEqualTo("CBS-2024-001");
        assertThat(ref.href()).isEqualTo("/api/v1/prets/CBS-2024-001");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("external avec id null/vide → retourne null")
    void external_id_null_retourne_null(String id) {
        assertThat(ResourceRef.external("prets", id)).isNull();
    }

    // ── Cohérence des valeurs ─────────────────────────────────────────────────

    @Test
    @DisplayName("href = '/api/v1/' + type + '/' + uid — toujours cohérent")
    void href_coherent_avec_type_et_uid() {
        String type = "kyc-dossiers";
        String uid  = "some-uuid-value";
        ResourceRef ref = ResourceRef.of(type, uid);

        assertThat(ref.href()).isEqualTo("/api/v1/" + type + "/" + uid);
    }

    @Test
    @DisplayName("of et external produisent le même format href")
    void of_et_external_meme_format() {
        String id = "XYZ-001";
        ResourceRef internal = ResourceRef.of("collectes", id);
        ResourceRef external = ResourceRef.external("collectes", id);

        assertThat(internal.href()).isEqualTo(external.href());
    }
}
