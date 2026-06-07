package cm.imf.pipeline.service.impl;

import cm.imf.pipeline.dto.request.CollecteEpargneRequest;
import cm.imf.pipeline.dto.request.SyncCollectesRequest;
import cm.imf.pipeline.dto.response.CollecteEpargneResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.dto.response.SyncCollectesResponse;
import cm.imf.pipeline.entity.Agence;
import cm.imf.pipeline.entity.CollecteEpargne;
import cm.imf.pipeline.entity.CycleCollecte;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.exception.ResourceNotFoundException;
import cm.imf.pipeline.repository.AgenceRepository;
import cm.imf.pipeline.repository.CollecteEpargneRepository;
import cm.imf.pipeline.repository.CycleCollecteRepository;
import cm.imf.pipeline.security.TenantContext;
import cm.imf.pipeline.service.ICollecteEpargneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollecteEpargneServiceImpl implements ICollecteEpargneService {

    private final CollecteEpargneRepository collecteRepo;
    private final AgenceRepository          agenceRepo;
    private final CycleCollecteRepository   cycleRepo;
    private final JdbcTemplate              jdbc;

    // ── soumettre ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CollecteEpargneResponse soumettre(CollecteEpargneRequest request) {
        User   agent = TenantContext.currentUser();
        Long   imfId = TenantContext.currentImfId();

        // Déduplication par UUID mobile
        if (collecteRepo.existsByUuidMobile(request.uuidMobile())) {
            CollecteEpargne existing = collecteRepo.findByUuidMobile(request.uuidMobile()).get();
            log.debug("Collecte épargne dupliquée (UUID) : {}", request.uuidMobile());
            return toResponse(existing);
        }

        CollecteEpargne collecte = CollecteEpargne.builder()
                .uuidMobile(request.uuidMobile())
                .imf(agent.getImf())
                .agent(agent)
                .agence(resolveAgence(request.agenceUid(), imfId))
                .cycle(resolveCycle(request.cycleUid()))
                .clientIdExterne(request.clientIdExterne())
                .montantCollecte(request.montantCollecte())
                .dateCollecte(request.dateCollecte())
                .heureCollecte(request.heureCollecte())
                .canalPaiement(request.canalPaiement())
                .referenceTransaction(request.referenceTransaction())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .precisionGpsMetres(request.precisionGpsMetres())
                .observation(request.observation())
                .statut("SOUMISE")
                .syncedAt(OffsetDateTime.now())
                .build();

        CollecteEpargne saved = collecteRepo.save(collecte);
        log.info("Collecte épargne soumise — agent: {}, client: {}, montant: {}",
                agent.getUsername(), request.clientIdExterne(), request.montantCollecte());
        return toResponse(saved);
    }

    // ── syncBatch ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public SyncCollectesResponse syncBatch(SyncCollectesRequest request) {
        int totalRecu  = request.collectes().size();
        int acceptees  = 0;
        int doublons   = 0;
        int rejetees   = 0;
        List<UUID> uuidsAcceptes = new ArrayList<>();
        List<UUID> uuidsDoublons = new ArrayList<>();
        List<SyncCollectesResponse.RejectionDetail> details = new ArrayList<>();

        for (CollecteEpargneRequest req : request.collectes()) {
            try {
                if (collecteRepo.existsByUuidMobile(req.uuidMobile())) {
                    doublons++;
                    uuidsDoublons.add(req.uuidMobile());
                } else {
                    soumettre(req);
                    acceptees++;
                    uuidsAcceptes.add(req.uuidMobile());
                }
            } catch (Exception e) {
                rejetees++;
                details.add(new SyncCollectesResponse.RejectionDetail(req.uuidMobile(), e.getMessage()));
                log.warn("Rejet collecte épargne {} : {}", req.uuidMobile(), e.getMessage());
            }
        }

        log.info("Sync batch épargne — total: {}, acceptées: {}, doublons: {}, rejetées: {}",
                totalRecu, acceptees, doublons, rejetees);
        return new SyncCollectesResponse(totalRecu, acceptees, doublons, rejetees,
                uuidsAcceptes, uuidsDoublons, details);
    }

    // ── valider ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CollecteEpargneResponse valider(UUID uid, String motifRejet) {
        Long imfId = TenantContext.currentImfId();
        User valideur = TenantContext.currentUser();

        CollecteEpargne c = (imfId != null
                ? collecteRepo.findByUidAndImf_Id(uid, imfId)
                : collecteRepo.findByUid(uid))
                .orElseThrow(() -> new ResourceNotFoundException("CollecteEpargne", uid));

        if (!"SOUMISE".equals(c.getStatut())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Seules les collectes SOUMISES peuvent être validées/rejetées (statut actuel : "
                    + c.getStatut() + ")");
        }

        boolean rejet = motifRejet != null && !motifRejet.isBlank();
        c.setStatut(rejet ? "REJETEE" : "VALIDEE");
        if (rejet) c.setMotifRejet(motifRejet);
        c.setValidatedBy(valideur);
        c.setValidatedAt(OffsetDateTime.now());

        return toResponse(collecteRepo.save(c));
    }

    // ── lister ────────────────────────────────────────────────────────────────

    @Override
    public PageResponse<CollecteEpargneResponse> lister(
            Long imfId, Long agenceId, Long agentId,
            LocalDate dateDebut, LocalDate dateFin,
            String statut, int page, int size) {

        Long effectiveImfId = imfId != null ? imfId : TenantContext.currentImfId();

        // Construction SQL dynamique
        StringBuilder sql = new StringBuilder("""
                SELECT c.* FROM app.collectes_epargne c
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();

        if (effectiveImfId != null) {
            sql.append(" AND c.imf_id = ?");
            params.add(effectiveImfId);
        }
        if (agenceId != null) {
            sql.append(" AND c.agence_id = ?");
            params.add(agenceId);
        }
        if (agentId != null) {
            sql.append(" AND c.agent_id = ?");
            params.add(agentId);
        }
        if (dateDebut != null) {
            sql.append(" AND c.date_collecte >= ?");
            params.add(dateDebut);
        }
        if (dateFin != null) {
            sql.append(" AND c.date_collecte <= ?");
            params.add(dateFin);
        }
        if (statut != null && !statut.isBlank()) {
            sql.append(" AND c.statut = ?");
            params.add(statut);
        }

        // Count total
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") sub";
        Long total = jdbc.queryForObject(countSql, Long.class, params.toArray());
        if (total == null) total = 0L;

        // Page
        sql.append(" ORDER BY c.created_at DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add((long) page * size);

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        List<CollecteEpargneResponse> content = rows.stream()
                .map(this::rowToResponse)
                .toList();

        return PageResponse.of(content, page, size, total);
    }

    // ── collectesNonSynchros ──────────────────────────────────────────────────

    @Override
    public List<CollecteEpargneResponse> collectesNonSynchros(Long agentId) {
        Long effectiveAgentId = agentId != null ? agentId : TenantContext.currentUser().getId();
        return collecteRepo
                .findByAgent_IdAndSyncedAtIsNullOrderByDateCollecteDesc(effectiveAgentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── kpiJour ───────────────────────────────────────────────────────────────

    @Override
    public CollecteEpargneResponse.KpiJour kpiJour(Long agentId, LocalDate date) {
        Long effectiveAgentId = agentId != null ? agentId : TenantContext.currentUser().getId();

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT
                    COUNT(*)                                                             AS nb_collectes,
                    COALESCE(SUM(montant_collecte), 0)                                   AS montant_total,
                    COALESCE(SUM(montant_collecte) FILTER (WHERE canal_paiement = 'ESPECES'), 0)
                                                                                         AS montant_especes,
                    COALESCE(SUM(montant_collecte) FILTER (WHERE canal_paiement != 'ESPECES'), 0)
                                                                                         AS montant_mobile_money,
                    COUNT(DISTINCT client_id_externe)                                    AS nb_clients
                FROM app.collectes_epargne
                WHERE agent_id = ? AND date_collecte = ?
                  AND statut != 'REJETEE'
                """, effectiveAgentId, date);

        return new CollecteEpargneResponse.KpiJour(
                date,
                ((Number) row.get("nb_collectes")).intValue(),
                toBd(row.get("montant_total")),
                toBd(row.get("montant_especes")),
                toBd(row.get("montant_mobile_money")),
                ((Number) row.get("nb_clients")).intValue()
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CollecteEpargneResponse toResponse(CollecteEpargne c) {
        return new CollecteEpargneResponse(
                c.getUid() != null ? c.getUid().toString() : null,
                c.getUuidMobile(),
                c.getClientIdExterne(),
                c.getCycle() != null && c.getCycle().getUid() != null ? c.getCycle().getUid().toString() : null,
                c.getCycle() != null ? c.getCycle().getNomCycle() : null,
                c.getAgent() != null && c.getAgent().getUid() != null ? c.getAgent().getUid().toString() : null,
                c.getAgent() != null ? c.getAgent().getUsername() : null,
                c.getAgence() != null && c.getAgence().getUid() != null ? c.getAgence().getUid().toString() : null,
                c.getAgence() != null ? c.getAgence().getNom() : null,
                c.getMontantCollecte(),
                c.getDateCollecte(),
                c.getHeureCollecte(),
                c.getCanalPaiement(),
                c.getReferenceTransaction(),
                c.getLatitude(),
                c.getLongitude(),
                c.getStatut(),
                c.getMotifRejet(),
                c.getObservation(),
                c.getSyncedAt(),
                c.getCreatedAt()
        );
    }

    private CollecteEpargneResponse rowToResponse(Map<String, Object> row) {
        return new CollecteEpargneResponse(
                row.get("uid") != null ? row.get("uid").toString() : null,
                row.get("uuid_mobile") != null ? UUID.fromString(row.get("uuid_mobile").toString()) : null,
                (String) row.get("client_id_externe"),
                null, // cycleUid non chargé en mode Jdbc pour éviter N+1
                null, // nomCycle non chargé
                null, // agentUid non chargé
                null, // agentUsername non chargé
                null, // agenceUid non chargé
                null, // nomAgence non chargé
                toBd(row.get("montant_collecte")),
                row.get("date_collecte") != null
                        ? ((java.sql.Date) row.get("date_collecte")).toLocalDate() : null,
                null, // heureCollecte
                (String) row.get("canal_paiement"),
                (String) row.get("reference_transaction"),
                toBd(row.get("latitude")),
                toBd(row.get("longitude")),
                (String) row.get("statut"),
                (String) row.get("motif_rejet"),
                (String) row.get("observation"),
                null, // syncedAt
                null  // createdAt
        );
    }

    private Agence resolveAgence(UUID agenceUid, Long imfId) {
        if (agenceUid == null) return null;
        return agenceRepo.findByUid(agenceUid)
                .filter(a -> a.getImf() != null && a.getImf().getId().equals(imfId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Agence " + agenceUid + " inconnue ou hors tenant"));
    }

    private CycleCollecte resolveCycle(UUID cycleUid) {
        if (cycleUid == null) return null;
        return cycleRepo.findByUid(cycleUid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Cycle " + cycleUid + " inconnu"));
    }

    private BigDecimal toBd(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        try {
            return new BigDecimal(val.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
