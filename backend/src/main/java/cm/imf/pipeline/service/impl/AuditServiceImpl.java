package cm.imf.pipeline.service.impl;

import cm.imf.pipeline.entity.JournalAudit;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.repository.JournalAuditRepository;
import cm.imf.pipeline.security.TenantContext;
import cm.imf.pipeline.service.IAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation du service d'audit.
 * Les writes sont @Async pour ne pas impacter les transactions métier.
 * Propagation REQUIRES_NEW : l'audit est committé même si la tx parent rollback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements IAuditService {

    private final JournalAuditRepository journalAuditRepository;

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User user, String action, String entite, String entiteId,
                    String details, String ipClient) {
        JournalAudit entry = JournalAudit.builder()
                .utilisateur(user)
                .username(user != null ? user.getUsername() : "SYSTEM")
                .action(action)
                .entite(entite)
                .entiteId(entiteId)
                .details(details)
                .ipClient(ipClient)
                .imf(user != null ? user.getImf() : TenantContext.currentImf())
                .statut("SUCCES")
                .build();
        journalAuditRepository.save(entry);
        log.debug("Audit: {} — {} [{}={}]", action,
                user != null ? user.getUsername() : "SYSTEM", entite, entiteId);
    }

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEchec(String username, String action, String details, String ipClient) {
        JournalAudit entry = JournalAudit.builder()
                .username(username != null ? username : "ANONYMOUS")
                .action(action)
                .details(details)
                .ipClient(ipClient)
                .statut("ECHEC")
                .build();
        journalAuditRepository.save(entry);
        log.warn("Audit ECHEC: {} — user: {}", action, username);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<JournalAudit> getHistorique(String username, int page, int size) {
        return journalAuditRepository.findByUsername(username,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<JournalAudit> getByAction(String action, int page, int size) {
        return journalAuditRepository.findByAction(action,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }
}
