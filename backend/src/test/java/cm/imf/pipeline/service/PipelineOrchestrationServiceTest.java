package cm.imf.pipeline.service;

import cm.imf.pipeline.sse.SseEmitterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PipelineOrchestrationService — statut enrichi")
class PipelineOrchestrationServiceTest {

    @Mock JdbcTemplate jdbc;
    @Mock SseEmitterRegistry sse;

    PipelineOrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new PipelineOrchestrationService(jdbc, sse);
        ReflectionTestUtils.setField(service, "airflowUrl", "");
        service.startPool();
    }

    @AfterEach
    void tearDown() {
        service.stopPool();
    }

    @Test
    @DisplayName("status() renseigne les lignes lues/écrites même sans schéma Airflow")
    void status_seed_has_line_counts() {
        when(jdbc.queryForList(anyString())).thenThrow(new RuntimeException("no airflow"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(48);

        var status = service.status();

        assertThat(status.dags()).isNotEmpty();
        assertThat(status.dags().get(0).lignesLues()).isPositive();
        assertThat(status.dags().get(0).lignesEcrites()).isNotNull();
        assertThat(status.statutGlobal()).isEqualTo("FAILED");
    }
}
