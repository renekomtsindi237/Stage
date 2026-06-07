package cm.imf.pipeline.sse;

import cm.imf.pipeline.dto.response.SseEventDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SseEmitterRegistry — tests unitaires")
class SseEmitterRegistryTest {

    private SseEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry(new ObjectMapper()
                .findAndRegisterModules());
    }

    @Test
    @DisplayName("register — crée un emitter non null")
    void register_cree_emitter() {
        SseEmitter emitter = registry.register("user01", "ANALYSTE");
        assertThat(emitter).isNotNull();
        assertThat(registry.getConnectedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("register — connexion en double remplace l'ancienne (page refresh)")
    void register_double_connexion_remplace() {
        registry.register("user01", "ANALYSTE");
        registry.register("user01", "ANALYSTE");
        assertThat(registry.getConnectedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("register — plusieurs utilisateurs coexistent")
    void register_plusieurs_utilisateurs() {
        registry.register("user01", "ANALYSTE");
        registry.register("user02", "DIRECTEUR");
        registry.register("user03", "AGENT");
        assertThat(registry.getConnectedCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("broadcastAll — n'échoue pas si aucun client connecté")
    void broadcastAll_sans_client_ne_plante_pas() {
        assertThatCode(() -> registry.broadcastAll(SseEventDto.heartbeat()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("broadcastToRole — sans client du rôle cible : pas d'erreur")
    void broadcastToRole_sans_client_cible() {
        registry.register("user01", "ANALYSTE");
        assertThatCode(() ->
                registry.broadcastToRole("DIRECTEUR", SseEventDto.heartbeat()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("getConnectedCount — commence à 0")
    void getConnectedCount_initial_zero() {
        assertThat(registry.getConnectedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("sendHeartbeat — sans client : ne plante pas")
    void sendHeartbeat_sans_client() {
        assertThatCode(() -> registry.sendHeartbeat()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SseEventDto.heartbeat — type et message corrects")
    void sseEventDto_heartbeat_format() {
        SseEventDto hb = SseEventDto.heartbeat();
        assertThat(hb.type()).isEqualTo(SseEventDto.TYPE_HEARTBEAT);
        assertThat(hb.message()).isEqualTo("ping");
        assertThat(hb.timestamp()).isNotNull();
        assertThat(hb.payload()).isNull();
    }

    @Test
    @DisplayName("SseEventDto.pipelineStatus — contient dagId et success")
    void sseEventDto_pipelineStatus_contient_payload() {
        SseEventDto dto = SseEventDto.pipelineStatus("dag_alertes", true, "Pipeline terminé.");
        assertThat(dto.type()).isEqualTo(SseEventDto.TYPE_PIPELINE_STATUS);
        assertThat(dto.targetRole()).isEqualTo("DSI");
        assertThat(dto.payload()).isNotNull();
        assertThat(dto.payload().toString()).contains("dag_alertes");
    }
}
