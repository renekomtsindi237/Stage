package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.response.AgentResponse;
import cm.imf.pipeline.exception.ResourceNotFoundException;
import cm.imf.pipeline.service.impl.AgentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentServiceImpl — tests unitaires (mock JdbcTemplate)")
class AgentServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private AgentServiceImpl agentService;

    private static final AgentResponse AGENT_A = new AgentResponse("AG001", "Amadou Diallo", "ANC01", "Agence Nord");
    private static final AgentResponse AGENT_B = new AgentResponse("AG002", "Binta Koné", "ANC01", "Agence Nord");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(agentService, "stagingSchema", "staging");
    }

    @Test
    @DisplayName("listByAgence — retourne les agents de l'agence demandée")
    void listByAgence_retourne_agents() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("ANC01")))
                .thenReturn(List.of(AGENT_A, AGENT_B));

        List<AgentResponse> result = agentService.listByAgence("ANC01");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AgentResponse::idAgent)
                .containsExactlyInAnyOrder("AG001", "AG002");
    }

    @Test
    @DisplayName("listByAgence — liste vide si aucun agent dans l'agence")
    void listByAgence_vide() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(List.of());

        List<AgentResponse> result = agentService.listByAgence("INCONNUE");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("count — retourne le nombre total d'agents")
    void count_retourne_total() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(42L);

        assertThat(agentService.count()).isEqualTo(42L);
    }

    @Test
    @DisplayName("getById — lève ResourceNotFoundException si agent inexistant")
    void getById_leve_exception_si_inconnu() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("INCONNU")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> agentService.getById("INCONNU"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getById — retourne l'agent si trouvé")
    void getById_retourne_agent() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("AG001")))
                .thenReturn(List.of(AGENT_A));

        AgentResponse result = agentService.getById("AG001");

        assertThat(result.idAgent()).isEqualTo("AG001");
        assertThat(result.nomAgent()).isEqualTo("Amadou Diallo");
    }

    @Test
    @DisplayName("search — retourne les agents dont le nom contient le terme")
    void search_retourne_resultats() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString(), eq(10)))
                .thenReturn(List.of(AGENT_A));

        List<AgentResponse> result = agentService.search("Amadou", 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nomAgent()).isEqualTo("Amadou Diallo");
    }
}
