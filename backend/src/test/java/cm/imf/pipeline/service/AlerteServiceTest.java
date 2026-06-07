package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.AlerteUpdateRequest;
import cm.imf.pipeline.dto.response.AlerteResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.entity.AlerteImpaye;
import cm.imf.pipeline.enums.StatutAlerte;
import cm.imf.pipeline.repository.AlerteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlerteService — tests unitaires")
class AlerteServiceTest {

    @Mock AlerteRepository alerteRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks AlerteService alerteService;

    private AlerteImpaye activeAlerte;

    @BeforeEach
    void setUp() {
        activeAlerte = AlerteImpaye.builder()
                .id(1L)
                .idPret("PRE-001")
                .joursRetard(35)
                .montantEnRetard(new BigDecimal("150000"))
                .statutAlerte(StatutAlerte.ACTIVE)
                .dateGeneration(OffsetDateTime.now().minusDays(2))
                .build();
    }

    @Test
    @DisplayName("getAlertes — sans filtre statut → retourne toutes les alertes")
    void getAlertes_sans_filtre_retourne_tout() {
        when(alerteRepository.findByImfId(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activeAlerte)));

        PageResponse<AlerteResponse> page = alerteService.getAlertes(null, 0, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).idPret()).isEqualTo("PRE-001");
    }

    @Test
    @DisplayName("getAlertes — avec filtre ACTIVE → appelle findByImfIdAndStatutAlerte")
    void getAlertes_avec_filtre_statut() {
        when(alerteRepository.findByImfIdAndStatutAlerte(any(), eq(StatutAlerte.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activeAlerte)));

        PageResponse<AlerteResponse> page = alerteService.getAlertes(StatutAlerte.ACTIVE, 0, 20);

        assertThat(page.content()).hasSize(1);
        verify(alerteRepository).findByImfIdAndStatutAlerte(any(), eq(StatutAlerte.ACTIVE), any());
        verify(alerteRepository, never()).findByImfId(any(), any(Pageable.class));
    }

    @Test
    @DisplayName("updateStatut — ACTIVE → ESCALADEE : transition valide")
    void updateStatut_active_vers_escaladee() {
        UUID uid = UUID.randomUUID();
        when(alerteRepository.findByUid(uid)).thenReturn(Optional.of(activeAlerte));
        when(alerteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AlerteResponse result = alerteService.updateStatut(uid, new AlerteUpdateRequest(StatutAlerte.ESCALADEE));

        assertThat(result.statutAlerte()).isEqualTo(StatutAlerte.ESCALADEE);
    }

    @Test
    @DisplayName("updateStatut — ACTIVE → CLOTUREE : dateCloture renseignée")
    void updateStatut_active_vers_cloturee_remplit_date_cloture() {
        UUID uid = UUID.randomUUID();
        when(alerteRepository.findByUid(uid)).thenReturn(Optional.of(activeAlerte));
        when(alerteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AlerteResponse result = alerteService.updateStatut(uid, new AlerteUpdateRequest(StatutAlerte.CLOTUREE));

        assertThat(result.statutAlerte()).isEqualTo(StatutAlerte.CLOTUREE);
        assertThat(result.dateCloture()).isNotNull();
    }

    @Test
    @DisplayName("updateStatut — alerte déjà CLOTUREE → 422 UNPROCESSABLE_ENTITY")
    void updateStatut_alerte_cloturee_leve_exception() {
        activeAlerte.setStatutAlerte(StatutAlerte.CLOTUREE);
        when(alerteRepository.findByUid(any(UUID.class))).thenReturn(Optional.of(activeAlerte));

        assertThatThrownBy(() ->
                alerteService.updateStatut(UUID.randomUUID(), new AlerteUpdateRequest(StatutAlerte.ACTIVE)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("clôturée");
    }

    @Test
    @DisplayName("getById — ID inconnu → 404")
    void getById_inconnu_leve_not_found() {
        when(alerteRepository.findByUid(any(UUID.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alerteService.getById(UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("countActiveAlertes — délègue au repository")
    void countActiveAlertes() {
        when(alerteRepository.countByImfIdAndStatutAlerte(any(), eq(StatutAlerte.ACTIVE))).thenReturn(7L);
        assertThat(alerteService.countActiveAlertes()).isEqualTo(7L);
    }
}
