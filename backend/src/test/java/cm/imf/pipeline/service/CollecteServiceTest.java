package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.CollecteRequest;
import cm.imf.pipeline.dto.response.CollecteResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.entity.CollecteTerrain;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.CanalPaiement;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.enums.StatutCollecte;
import cm.imf.pipeline.repository.CollecteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

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
@DisplayName("CollecteService — tests unitaires")
class CollecteServiceTest {

    @Mock CollecteRepository collecteRepository;
    @InjectMocks CollecteService collecteService;

    private User agent;
    private CollecteRequest validRequest;

    @BeforeEach
    void setUp() {
        agent = User.builder()
                .id(1L).username("agent01").role(Role.AGENT).actif(true).build();

        validRequest = new CollecteRequest(
                "MOBILE-UUID-001",
                "CLI001",
                "PRE001",
                LocalDate.now(),
                new BigDecimal("25000.00"),
                CanalPaiement.MTN,
                "REF-MTN-2024-001",
                "Collecte terrain agence Nord",
                new BigDecimal("3.8613"),
                new BigDecimal("11.5166")
        );
    }

    @Test
    @DisplayName("enregistrer — nouvelle collecte → statut CONFIRMEE")
    void enregistrer_nouvelle_collecte_retourne_confirmee() {
        when(collecteRepository.existsByIdCollecteMobile("MOBILE-UUID-001")).thenReturn(false);
        when(collecteRepository.existsByReferenceTransactionAndDateCollecte(any(), any())).thenReturn(false);
        when(collecteRepository.save(any())).thenAnswer(inv -> {
            CollecteTerrain c = inv.getArgument(0);
            c.setId(10L);
            c.setCreatedAt(OffsetDateTime.now());
            return c;
        });

        CollecteResponse result = collecteService.enregistrer(validRequest, agent);

        assertThat(result.statut()).isEqualTo(StatutCollecte.CONFIRMEE);
        assertThat(result.idCollecteMobile()).isEqualTo("MOBILE-UUID-001");
        assertThat(result.montantCollecte()).isEqualByComparingTo("25000.00");
        verify(collecteRepository).save(any(CollecteTerrain.class));
    }

    @Test
    @DisplayName("enregistrer — ID mobile déjà connu → statut DOUBLON")
    void enregistrer_id_mobile_existant_retourne_doublon() {
        CollecteTerrain existing = CollecteTerrain.builder()
                .id(5L)
                .idCollecteMobile("MOBILE-UUID-001")
                .statut(StatutCollecte.CONFIRMEE)
                .build();

        when(collecteRepository.existsByIdCollecteMobile("MOBILE-UUID-001")).thenReturn(true);
        when(collecteRepository.findByIdCollecteMobile("MOBILE-UUID-001"))
                .thenReturn(Optional.of(existing));
        when(collecteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CollecteResponse result = collecteService.enregistrer(validRequest, agent);

        assertThat(result.statut()).isEqualTo(StatutCollecte.DOUBLON);
    }

    @Test
    @DisplayName("enregistrer — référence transaction dupliquée → 409 CONFLICT")
    void enregistrer_reference_dupliquee_leve_exception() {
        when(collecteRepository.existsByIdCollecteMobile(any())).thenReturn(false);
        when(collecteRepository.existsByReferenceTransactionAndDateCollecte(
                "REF-MTN-2024-001", LocalDate.now())).thenReturn(true);

        assertThatThrownBy(() -> collecteService.enregistrer(validRequest, agent))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Doublon");
    }

    @Test
    @DisplayName("getById — ID inexistant → 404")
    void getById_inexistant_leve_not_found() {
        when(collecteRepository.findByUid(any(UUID.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> collecteService.getById(UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("getMesCollectes — retourne page paginée de l'agent")
    void getMesCollectes_retourne_page() {
        CollecteTerrain c = CollecteTerrain.builder()
                .id(1L).idCollecteMobile("X").agent(agent)
                .statut(StatutCollecte.CONFIRMEE).createdAt(OffsetDateTime.now()).build();

        when(collecteRepository.findByImfIdAndAgentId(isNull(), eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(c)));

        PageResponse<CollecteResponse> page = collecteService.getMesCollectes(agent, 0, 10);

        assertThat(page.content()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1L);
    }
}
