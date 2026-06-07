package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.EcheanceUpdateRequest;
import cm.imf.pipeline.dto.response.EcheanceResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.entity.EcheanceApp;
import cm.imf.pipeline.enums.StatutEcheance;
import cm.imf.pipeline.exception.ResourceNotFoundException;
import cm.imf.pipeline.repository.EcheanceAppRepository;
import cm.imf.pipeline.service.impl.EcheanceServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EcheanceServiceImpl — tests unitaires")
class EcheanceServiceImplTest {

    @Mock
    private EcheanceAppRepository echeanceRepository;

    @InjectMocks
    private EcheanceServiceImpl echeanceService;

    private EcheanceApp buildEcheance(Long id, String idPret, StatutEcheance statut) {
        return EcheanceApp.builder()
                .id(id)
                .idPret(idPret)
                .numEcheance(1)
                .dateEcheance(LocalDate.now().plusMonths(1))
                .montantDu(new BigDecimal("50000"))
                .montantPaye(BigDecimal.ZERO)
                .statut(statut)
                .build();
    }

    @Test
    @DisplayName("getByPret — retourne les échéances ordonnées")
    void getByPret_retourne_liste() {
        EcheanceApp e1 = buildEcheance(1L, "PRE-001", StatutEcheance.EN_ATTENTE);
        EcheanceApp e2 = buildEcheance(2L, "PRE-001", StatutEcheance.PAYEE);
        when(echeanceRepository.findByIdPretOrderByNumEcheanceAsc("PRE-001"))
                .thenReturn(List.of(e1, e2));

        List<EcheanceResponse> result = echeanceService.getByPret("PRE-001");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).idPret()).isEqualTo("PRE-001");
    }

    @Test
    @DisplayName("getByPret — liste vide si prêt sans échéances")
    void getByPret_vide() {
        when(echeanceRepository.findByIdPretOrderByNumEcheanceAsc("INCONNU")).thenReturn(List.of());
        assertThat(echeanceService.getByPret("INCONNU")).isEmpty();
    }

    @Test
    @DisplayName("getById — retourne l'échéance si trouvée")
    void getById_retourne_echeance() {
        UUID uid = UUID.randomUUID();
        EcheanceApp e = buildEcheance(1L, "PRE-001", StatutEcheance.EN_ATTENTE);
        when(echeanceRepository.findByUid(uid)).thenReturn(Optional.of(e));

        EcheanceResponse result = echeanceService.getById(uid);
        assertThat(result.idPret()).isEqualTo("PRE-001");
        assertThat(result.statut()).isEqualTo(StatutEcheance.EN_ATTENTE);
    }

    @Test
    @DisplayName("getById — lève ResourceNotFoundException si non trouvée")
    void getById_leve_exception() {
        when(echeanceRepository.findByUid(any(UUID.class))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> echeanceService.getById(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updateStatut — met à jour correctement")
    void updateStatut_met_a_jour() {
        UUID uid = UUID.randomUUID();
        EcheanceApp e = buildEcheance(1L, "PRE-001", StatutEcheance.EN_ATTENTE);
        when(echeanceRepository.findByUid(uid)).thenReturn(Optional.of(e));
        when(echeanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EcheanceUpdateRequest req = new EcheanceUpdateRequest(
                StatutEcheance.PAYEE, new BigDecimal("50000"), LocalDate.now(), "Payé intégralement");

        EcheanceResponse result = echeanceService.updateStatut(uid, req);

        assertThat(result.statut()).isEqualTo(StatutEcheance.PAYEE);
        assertThat(result.montantPaye()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("updateStatut — lève 422 si échéance ANNULEE")
    void updateStatut_echec_si_annulee() {
        EcheanceApp e = buildEcheance(1L, "PRE-001", StatutEcheance.ANNULEE);
        when(echeanceRepository.findByUid(any(UUID.class))).thenReturn(Optional.of(e));

        EcheanceUpdateRequest req = new EcheanceUpdateRequest(StatutEcheance.PAYEE, null, null, null);

        assertThatThrownBy(() -> echeanceService.updateStatut(UUID.randomUUID(), req))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("getEcheancesEnRetard — délègue au repository avec statut EN_RETARD")
    void getEcheancesEnRetard_delegue() {
        when(echeanceRepository.findByImfIdAndStatut(any(), eq(StatutEcheance.EN_RETARD), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<EcheanceResponse> result = echeanceService.getEcheancesEnRetard(0, 10);
        assertThat(result.content()).isEmpty();
        verify(echeanceRepository).findByImfIdAndStatut(any(), eq(StatutEcheance.EN_RETARD), any(Pageable.class));
    }

    @Test
    @DisplayName("countEnRetard — retourne le bon compte")
    void countEnRetard_retourne_compte() {
        when(echeanceRepository.countByImfIdAndStatut(any(), eq(StatutEcheance.EN_RETARD))).thenReturn(7L);
        assertThat(echeanceService.countEnRetard()).isEqualTo(7L);
    }
}
