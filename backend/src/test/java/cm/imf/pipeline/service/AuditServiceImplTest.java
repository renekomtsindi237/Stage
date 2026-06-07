package cm.imf.pipeline.service;

import cm.imf.pipeline.entity.JournalAudit;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.repository.JournalAuditRepository;
import cm.imf.pipeline.service.impl.AuditServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditServiceImpl — tests unitaires")
class AuditServiceImplTest {

    @Mock
    private JournalAuditRepository journalAuditRepository;

    @InjectMocks
    private AuditServiceImpl auditService;

    private User buildUser() {
        return User.builder()
                .id(1L)
                .username("agent01")
                .role(Role.AGENT)
                .actif(true)
                .build();
    }

    @Test
    @DisplayName("log — crée une entrée SUCCES avec les bonnes données")
    void log_cree_entree_succes() {
        User user = buildUser();
        when(journalAuditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        auditService.log(user, "COLLECTE_SOUMISE", "CollecteTerrain", "42",
                "{\"montant\": 5000}", "192.168.1.1");

        ArgumentCaptor<JournalAudit> captor = ArgumentCaptor.forClass(JournalAudit.class);
        verify(journalAuditRepository).save(captor.capture());
        JournalAudit saved = captor.getValue();

        assertThat(saved.getUsername()).isEqualTo("agent01");
        assertThat(saved.getAction()).isEqualTo("COLLECTE_SOUMISE");
        assertThat(saved.getEntite()).isEqualTo("CollecteTerrain");
        assertThat(saved.getEntiteId()).isEqualTo("42");
        assertThat(saved.getStatut()).isEqualTo("SUCCES");
        assertThat(saved.getIpClient()).isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("log — null user produit username=SYSTEM")
    void log_null_user_produit_system() {
        when(journalAuditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        auditService.log(null, "PIPELINE_EVENT", "Pipeline", "dag_alertes", null, null);

        ArgumentCaptor<JournalAudit> captor = ArgumentCaptor.forClass(JournalAudit.class);
        verify(journalAuditRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("logEchec — crée une entrée ECHEC")
    void logEchec_cree_entree_echec() {
        when(journalAuditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        auditService.logEchec("hacker", "LOGIN", "Identifiants invalides", "10.0.0.1");

        ArgumentCaptor<JournalAudit> captor = ArgumentCaptor.forClass(JournalAudit.class);
        verify(journalAuditRepository).save(captor.capture());
        assertThat(captor.getValue().getStatut()).isEqualTo("ECHEC");
        assertThat(captor.getValue().getUsername()).isEqualTo("hacker");
    }

    @Test
    @DisplayName("logEchec — username null → ANONYMOUS")
    void logEchec_null_username_produit_anonymous() {
        when(journalAuditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        auditService.logEchec(null, "ACCESS_DENIED", "Token expiré", "1.2.3.4");

        ArgumentCaptor<JournalAudit> captor = ArgumentCaptor.forClass(JournalAudit.class);
        verify(journalAuditRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("ANONYMOUS");
    }

    @Test
    @DisplayName("getHistorique — délègue au repository avec la bonne pagination")
    void getHistorique_delegue_au_repository() {
        Page<JournalAudit> expected = new PageImpl<>(List.of());
        when(journalAuditRepository.findByUsername(eq("agent01"), any(Pageable.class)))
                .thenReturn(expected);

        Page<JournalAudit> result = auditService.getHistorique("agent01", 0, 10);

        assertThat(result).isSameAs(expected);
        verify(journalAuditRepository).findByUsername(eq("agent01"), any(Pageable.class));
    }

    @Test
    @DisplayName("getByAction — délègue au repository avec la bonne action")
    void getByAction_delegue_au_repository() {
        Page<JournalAudit> expected = new PageImpl<>(List.of());
        when(journalAuditRepository.findByAction(eq("ALERTE_CLOTUREE"), any(Pageable.class)))
                .thenReturn(expected);

        Page<JournalAudit> result = auditService.getByAction("ALERTE_CLOTUREE", 0, 20);

        assertThat(result).isSameAs(expected);
    }
}
