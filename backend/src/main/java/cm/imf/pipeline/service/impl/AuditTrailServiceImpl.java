package cm.imf.pipeline.service.impl;

import cm.imf.pipeline.dto.response.AuditTrailResponse;
import cm.imf.pipeline.entity.AuditTrail;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.repository.AuditTrailRepository;
import cm.imf.pipeline.security.DataMaskingUtils;
import cm.imf.pipeline.security.TenantContext;
import cm.imf.pipeline.service.IAuditTrailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditTrailServiceImpl implements IAuditTrailService {

    private final AuditTrailRepository auditTrailRepository;

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enregistrer(String action, String entiteType, String entiteId,
                             Map<String, Object> ancienneValeur, Map<String, Object> nouvelleValeur,
                             String motif, String ipClient, String userAgent) {
        User acteur = TenantContext.currentUser();

        AuditTrail entry = AuditTrail.builder()
                .imfId(acteur != null && acteur.getImf() != null ? acteur.getImf().getId() : null)
                .acteurId(acteur != null ? acteur.getId() : null)
                .acteurUsername(acteur != null ? acteur.getUsername() : "SYSTEM")
                .acteurRole(acteur != null ? acteur.getRole().name() : "SYSTEM")
                .action(action)
                .entiteType(entiteType)
                .entiteId(entiteId)
                .ancienneValeur(ancienneValeur)
                .nouvelleValeur(nouvelleValeur)
                .motif(motif)
                .ipClient(ipClient)
                .userAgent(userAgent)
                .statut("SUCCES")
                .build();

        auditTrailRepository.save(entry);
        log.debug("AuditTrail: {} {} [{}={}] par {}",
                action, entiteType, entiteId, acteur != null ? acteur.getUsername() : "SYSTEM", motif);
    }

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enregistrerEchec(String action, String entiteType, String entiteId,
                                  String motif, String ipClient) {
        User acteur = TenantContext.currentUser();

        AuditTrail entry = AuditTrail.builder()
                .imfId(acteur != null && acteur.getImf() != null ? acteur.getImf().getId() : null)
                .acteurId(acteur != null ? acteur.getId() : null)
                .acteurUsername(acteur != null ? acteur.getUsername() : "ANONYMOUS")
                .acteurRole(acteur != null ? acteur.getRole().name() : "ANONYMOUS")
                .action(action)
                .entiteType(entiteType)
                .entiteId(entiteId)
                .motif(motif)
                .ipClient(ipClient)
                .statut("ECHEC")
                .build();

        auditTrailRepository.save(entry);
        log.warn("AuditTrail ECHEC: {} {} [{}]", action, entiteType, motif);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditTrailResponse> rechercher(Long imfId, String entiteType, String entiteId,
                                                String action, String username,
                                                OffsetDateTime debut, OffsetDateTime fin,
                                                int page, int size) {
        Role roleAppelant = roleAppelant();
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        return auditTrailRepository
                .rechercher(imfId, entiteType, entiteId, action, username, debut, fin, pageable)
                .map(entry -> toResponse(entry, roleAppelant));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditTrailResponse> historiqueEntite(Long imfId, String entiteType,
                                                      String entiteId, int page, int size) {
        Role roleAppelant = roleAppelant();
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        return auditTrailRepository
                .findByImfIdAndEntiteTypeAndEntiteIdOrderByCreatedAtDesc(imfId, entiteType, entiteId, pageable)
                .map(entry -> toResponse(entry, roleAppelant));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Role roleAppelant() {
        User user = TenantContext.currentUser();
        return user != null ? user.getRole() : Role.AGENT;
    }

    private AuditTrailResponse toResponse(AuditTrail entry, Role role) {
        return new AuditTrailResponse(
                entry.getId(),
                entry.getImfId(),
                entry.getActeurId(),
                entry.getActeurUsername(),
                entry.getActeurRole(),
                entry.getAction(),
                entry.getEntiteType(),
                entry.getEntiteId(),
                DataMaskingUtils.masquerJsonAudit(entry.getAncienneValeur(), role),
                DataMaskingUtils.masquerJsonAudit(entry.getNouvelleValeur(), role),
                entry.getMotif(),
                // IP masquée pour tout rôle autre que DSI/SUPER_ADMIN
                DataMaskingUtils.peutVoirDonneesCompletes(role) ? entry.getIpClient() : "***.***.***",
                entry.getStatut(),
                entry.getCreatedAt()
        );
    }
}
