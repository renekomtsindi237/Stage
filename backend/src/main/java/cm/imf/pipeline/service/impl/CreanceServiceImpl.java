package cm.imf.pipeline.service.impl;

import cm.imf.pipeline.dto.response.CreanceResponse;
import cm.imf.pipeline.dto.response.KpiRecouvrementResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.entity.Creance;
import cm.imf.pipeline.exception.ResourceNotFoundException;
import cm.imf.pipeline.entity.Agence;
import cm.imf.pipeline.repository.AgenceRepository;
import cm.imf.pipeline.repository.CreanceRepository;
import cm.imf.pipeline.security.TenantContext;
import cm.imf.pipeline.service.ICreanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreanceServiceImpl implements ICreanceService {

    private final CreanceRepository creanceRepo;
    private final AgenceRepository  agenceRepo;
    private final JdbcTemplate      jdbc;

    @Value("${imf.pipeline.ml-schema:ml}")
    private String mlSchema;

    // ── lister ────────────────────────────────────────────────────────────────

    @Override
    public PageResponse<CreanceResponse> lister(
            Long imfId, Long agenceId, String categoriePar,
            String statut, LocalDate dateDebut, LocalDate dateFin,
            int page, int size) {

        Long effectiveImfId = imfId != null ? imfId : TenantContext.currentImfId();
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        return PageResponse.from(
                creanceRepo.findFiltered(
                        effectiveImfId, agenceId, categoriePar,
                        statut, dateDebut, dateFin, pageable),
                c -> toResponse(c, null));
    }

    // ── detail ────────────────────────────────────────────────────────────────

    @Override
    public CreanceResponse detail(UUID uid) {
        Long imfId = TenantContext.currentImfId();
        Creance c = imfId != null
                ? creanceRepo.findByUidAndImf_Id(uid, imfId)
                             .orElseThrow(() -> new ResourceNotFoundException("Créance", uid))
                : creanceRepo.findByUid(uid)
                             .orElseThrow(() -> new ResourceNotFoundException("Créance", uid));

        CreanceResponse.ScoreMcrs score = tryGetScore(
                c.getImf().getId(), c.getClientIdExterne());
        return toResponse(c, score);
    }

    // ── kpiRecouvrement ───────────────────────────────────────────────────────

    @Override
    public KpiRecouvrementResponse kpiRecouvrement(Long imfId, UUID agenceUid, LocalDate datePeriode) {
        Long effectiveImfId = imfId != null ? imfId : TenantContext.currentImfId();
        Long agenceId = null;
        if (agenceUid != null) {
            agenceId = agenceRepo.findByUid(agenceUid)
                    .map(Agence::getId).orElse(null);
        }

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT
                    COUNT(*) FILTER (WHERE statut NOT IN ('SOLDEE','IRRECOVERABLE'))         AS nb_actives,
                    COUNT(*) FILTER (WHERE jours_retard >= 30)                               AS nb_probleme,
                    COALESCE(SUM(montant_impaye) FILTER (WHERE jours_retard >= 30),  0)      AS par30,
                    COALESCE(SUM(montant_impaye) FILTER (WHERE jours_retard >= 60),  0)      AS par60,
                    COALESCE(SUM(montant_impaye) FILTER (WHERE jours_retard >= 90),  0)      AS par90,
                    COALESCE(SUM(capital_restant_du) FILTER (WHERE statut NOT IN ('SOLDEE','IRRECOVERABLE')), 0) AS encours,
                    COALESCE(SUM(montant_provision), 0)                                      AS provisions,
                    COALESCE(SUM(montant_initial - montant_impaye)
                             FILTER (WHERE statut IN ('SOLDEE')), 0)                         AS recouvre
                FROM app.creances
                WHERE imf_id = ?
                  AND (? IS NULL OR agence_id = ?)
                """,
                effectiveImfId, agenceId, agenceId);

        BigDecimal encours   = toBd(row.get("encours"));
        BigDecimal par30     = toBd(row.get("par30"));
        BigDecimal par60     = toBd(row.get("par60"));
        BigDecimal par90     = toBd(row.get("par90"));
        BigDecimal provisions = toBd(row.get("provisions"));
        BigDecimal recouvre  = toBd(row.get("recouvre"));
        int nbActives  = ((Number) row.get("nb_actives")).intValue();
        int nbProbleme = ((Number) row.get("nb_probleme")).intValue();

        double par30Pct = encours.compareTo(BigDecimal.ZERO) > 0
                ? par30.divide(encours, 6, RoundingMode.HALF_UP).doubleValue() * 100 : 0.0;
        double par60Pct = encours.compareTo(BigDecimal.ZERO) > 0
                ? par60.divide(encours, 6, RoundingMode.HALF_UP).doubleValue() * 100 : 0.0;
        double par90Pct = encours.compareTo(BigDecimal.ZERO) > 0
                ? par90.divide(encours, 6, RoundingMode.HALF_UP).doubleValue() * 100 : 0.0;

        // Taux de recouvrement = montants soldés / (montants soldés + impayés actifs)
        BigDecimal base = recouvre.add(par30);
        double tauxRecouvrement = base.compareTo(BigDecimal.ZERO) > 0
                ? recouvre.divide(base, 6, RoundingMode.HALF_UP).doubleValue() * 100 : 100.0;

        // Benchmark agences si pas de filtre agence
        Integer rangAgence = null;
        Integer nbAgences  = null;
        if (agenceId != null) {
            try {
                List<Map<String, Object>> benchmark = jdbc.queryForList("""
                        SELECT agence_id,
                               SUM(montant_impaye) FILTER (WHERE jours_retard >= 30) AS par30_agence
                        FROM app.creances
                        WHERE imf_id = ?
                        GROUP BY agence_id
                        ORDER BY par30_agence DESC
                        """, effectiveImfId);
                nbAgences = benchmark.size();
                for (int i = 0; i < benchmark.size(); i++) {
                    Object aId = benchmark.get(i).get("agence_id");
                    if (aId != null && agenceId.equals(((Number) aId).longValue())) {
                        rangAgence = i + 1;
                        break;
                    }
                }
            } catch (Exception e) {
                log.debug("Benchmark agences KPI créances : {}", e.getMessage());
            }
        }

        return new KpiRecouvrementResponse(
                agenceUid != null ? agenceUid.toString() : null, datePeriode,
                par30, par60, par90,
                Math.round(par30Pct * 100.0) / 100.0,
                Math.round(par60Pct * 100.0) / 100.0,
                Math.round(par90Pct * 100.0) / 100.0,
                Math.round(tauxRecouvrement * 100.0) / 100.0,
                recouvre,
                provisions,
                encours,
                nbActives,
                nbProbleme,
                provisions,
                rangAgence,
                nbAgences
        );
    }

    // ── scoreClient ───────────────────────────────────────────────────────────

    @Override
    public CreanceResponse.ScoreMcrs scoreClient(Long imfId, String clientIdExterne) {
        Long effectiveImfId = imfId != null ? imfId : TenantContext.currentImfId();
        return tryGetScore(effectiveImfId, clientIdExterne);
    }

    // ── majStatut ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CreanceResponse majStatut(UUID uid, String nouveauStatut, String observation) {
        Long imfId = TenantContext.currentImfId();
        Creance c = imfId != null
                ? creanceRepo.findByUidAndImf_Id(uid, imfId)
                             .orElseThrow(() -> new ResourceNotFoundException("Créance", uid))
                : creanceRepo.findByUid(uid)
                             .orElseThrow(() -> new ResourceNotFoundException("Créance", uid));

        List<String> statutsValides = List.of("ACTIVE", "SOLDEE", "IRRECOVERABLE", "EN_RECOUVREMENT",
                "CONTENTIEUX", "RESTRUCTUREE");
        if (!statutsValides.contains(nouveauStatut)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Statut invalide : " + nouveauStatut + ". Valeurs acceptées : " + statutsValides);
        }

        c.setStatut(nouveauStatut);
        c.setUpdatedAt(OffsetDateTime.now());
        return toResponse(creanceRepo.save(c), null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CreanceResponse.ScoreMcrs tryGetScore(Long imfId, String clientIdExterne) {
        try {
            Map<String, Object> row = jdbc.queryForMap("""
                    SELECT score_crs, score_rps, score_csi, score_mcrs,
                           classe_risque, probabilite_defaut_90j,
                           action_recommandee, priorite_recouvrement,
                           top_feature, top_shap_value
                    FROM %s.client_scores
                    WHERE imf_id = ? AND client_id_externe = ?
                    ORDER BY scored_at DESC
                    LIMIT 1
                    """.formatted(mlSchema),
                    imfId, clientIdExterne);

            return new CreanceResponse.ScoreMcrs(
                    toDouble(row.get("score_crs")),
                    toDouble(row.get("score_rps")),
                    toDouble(row.get("score_csi")),
                    toDouble(row.get("score_mcrs")),
                    (String) row.get("classe_risque"),
                    toDouble(row.get("probabilite_defaut_90j")),
                    (String) row.get("action_recommandee"),
                    row.get("priorite_recouvrement") != null
                            ? ((Number) row.get("priorite_recouvrement")).intValue() : 0,
                    (String) row.get("top_feature"),
                    toDouble(row.get("top_shap_value"))
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            log.debug("Score MCRS non disponible pour client {} : {}", clientIdExterne, e.getMessage());
            return null;
        }
    }

    private CreanceResponse toResponse(Creance c, CreanceResponse.ScoreMcrs score) {
        return new CreanceResponse(
                c.getUid() != null ? c.getUid().toString() : null,
                c.getIdPretExterne(),
                c.getClientIdExterne(),
                c.getAgence() != null && c.getAgence().getUid() != null ? c.getAgence().getUid().toString() : null,
                c.getAgence() != null ? c.getAgence().getNom() : null,
                c.getMontantInitial(),
                c.getMontantImpaye(),
                c.getCapitalRestantDu(),
                c.getInteretsRetard(),
                c.getMontantProvision(),
                c.getJoursRetard(),
                c.getCategoriePar(),
                c.getClasseRisqueCobac(),
                c.getTauxProvisionCobac(),
                c.getTypeGarantie(),
                c.getStatut(),
                c.getAgentResponsable() != null ? c.getAgentResponsable().getUsername() : null,
                c.getDateOuvertureCreance(),
                score,
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    private BigDecimal toBd(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        return new BigDecimal(val.toString());
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        return ((Number) val).doubleValue();
    }
}
