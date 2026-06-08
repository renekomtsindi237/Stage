package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.response.SseEventDto;
import cm.imf.pipeline.ml.FeatureInputDto;
import cm.imf.pipeline.ml.MlScoringClient;
import cm.imf.pipeline.ml.ScoringResultDto;
import cm.imf.pipeline.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service de scoring temps réel déclenché après chaque synchronisation mobile.
 *
 * Flux :
 *  1. Pour chaque clientId : requête sur ml.features_client + ml.feat_client_externe
 *  2. Construction du FeatureInputDto (features DB + null → imputation FastAPI)
 *  3. Appel FastAPI POST /score/single
 *  4. UPSERT résultat dans ml.client_scores
 *  5. Notification SSE à l'agent + broadcast RESPONSABLE_RECOUVREMENT
 *
 * Ce service est appelé de façon synchrone depuis SyncEventListener qui est lui-même
 * exécuté en @Async — donc ce service ne bloque pas la réponse HTTP de sync.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeScoringService implements IRealtimeScoringService {

    private final MlScoringClient    mlScoringClient;
    private final JdbcTemplate       jdbc;
    private final SseEmitterRegistry sseRegistry;

    private static final String SQL_FEATURES = """
            SELECT
                fc.nb_collectes_12m,
                fc.regularite_collecte_pct,
                fc.tendance_collecte_3m,
                fc.montant_moy_collecte,
                fc.ecart_type_collecte,
                fc.nb_cycles_manques_12m,
                fc.montant_total_collectes_12m,
                fc.taux_remboursement_pct,
                fc.jours_retard_moyen,
                fc.jours_retard_max,
                fc.nb_incidents_paiement,
                fc.montant_impaye_courant,
                fc.nb_remboursements_12m,
                fc.classe_risque_cobac_encode,
                fc.revenu_mensuel_estime,
                fc.anciennete_client_jours,
                fc.nb_produits_actifs,
                fc.ratio_collecte_credit,
                fc.capacite_remboursement,
                fc.indice_resilience,
                fc.est_producteur,
                fe.prix_produit_principal_moy,
                fe.volatilite_prix_produit,
                fe.tendance_prix_30j,
                fe.inflation_mensuelle_moy,
                fe.taux_directeur_beac,
                fe.precipitation_moy_mm,
                fe.indice_secheresse,
                fe.nb_evenements_negatifs
            FROM ml.features_client fc
            LEFT JOIN ml.feat_client_externe fe
                ON fc.client_id_externe = fe.client_id_externe
               AND fc.imf_id = fe.imf_id
            WHERE fc.client_id_externe = ? AND fc.imf_id = ?
            """;

    private static final String SQL_UPSERT = """
            INSERT INTO ml.client_scores (
                client_id_externe, imf_id,
                score_crs, score_rps, score_csi, score_mcrs,
                niveau_risque, probabilite_defaut_30j, probabilite_defaut_90j,
                score_mcrs_ic_bas, score_mcrs_ic_haut,
                action_recommandee, priorite_recouvrement, scored_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (client_id_externe, imf_id) DO UPDATE SET
                score_crs                = EXCLUDED.score_crs,
                score_rps                = EXCLUDED.score_rps,
                score_csi                = EXCLUDED.score_csi,
                score_mcrs               = EXCLUDED.score_mcrs,
                niveau_risque            = EXCLUDED.niveau_risque,
                probabilite_defaut_30j   = EXCLUDED.probabilite_defaut_30j,
                probabilite_defaut_90j   = EXCLUDED.probabilite_defaut_90j,
                score_mcrs_ic_bas        = EXCLUDED.score_mcrs_ic_bas,
                score_mcrs_ic_haut       = EXCLUDED.score_mcrs_ic_haut,
                action_recommandee       = EXCLUDED.action_recommandee,
                priorite_recouvrement    = EXCLUDED.priorite_recouvrement,
                scored_at                = NOW()
            """;

    @Override
    public void scorerClientsApresSync(List<String> clientIds, Long imfId, String agentUsername) {
        if (clientIds.isEmpty()) return;

        String imfCode = String.valueOf(imfId);
        List<Map<String, Object>> scored = new ArrayList<>();

        for (String clientId : clientIds) {
            try {
                FeatureInputDto input = buildFeatureInput(clientId, imfCode, imfId);
                Optional<ScoringResultDto> resultOpt = mlScoringClient.scoreSingle(input);

                if (resultOpt.isEmpty()) {
                    log.warn("Scoring temps réel ignoré pour client {} — ML API indisponible", clientId);
                    continue;
                }

                ScoringResultDto result = resultOpt.get();
                upsertScore(result, imfId);

                scored.add(Map.of(
                    "clientId",         clientId,
                    "scoreMcrs",        result.scoreMcrs(),
                    "classeRisque",     result.classeRisque(),
                    "actionRecommandee", result.actionRecommandee()
                ));

                log.info("Score temps réel — client: {}, MCRS: {:.3f}, classe: {}",
                        clientId, result.scoreMcrs(), result.classeRisque());

            } catch (Exception e) {
                log.error("Erreur scoring temps réel client {} : {}", clientId, e.getMessage(), e);
            }
        }

        pushSseNotification(agentUsername, scored);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private FeatureInputDto buildFeatureInput(String clientId, String imfCode, Long imfId) {
        List<Map<String, Object>> rows = jdbc.queryForList(SQL_FEATURES, clientId, imfId);
        if (rows.isEmpty()) {
            log.debug("Aucune feature ML trouvée pour client {} — scoring avec valeurs par défaut", clientId);
            return FeatureInputDto.minimal(clientId, imfCode);
        }
        Map<String, Object> r = rows.get(0);
        return new FeatureInputDto(
            clientId, imfCode, null, null,
            toDouble(r, "nb_collectes_12m"),
            toDouble(r, "regularite_collecte_pct"),
            toDouble(r, "tendance_collecte_3m"),
            toDouble(r, "montant_moy_collecte"),
            toDouble(r, "ecart_type_collecte"),
            toDouble(r, "nb_cycles_manques_12m"),
            toDouble(r, "montant_total_collectes_12m"),
            toDouble(r, "taux_remboursement_pct"),
            toDouble(r, "jours_retard_moyen"),
            toDouble(r, "jours_retard_max"),
            toDouble(r, "nb_incidents_paiement"),
            toDouble(r, "montant_impaye_courant"),
            toDouble(r, "nb_remboursements_12m"),
            toDouble(r, "classe_risque_cobac_encode"),
            toDouble(r, "revenu_mensuel_estime"),
            toDouble(r, "anciennete_client_jours"),
            toDouble(r, "nb_produits_actifs"),
            toDouble(r, "ratio_collecte_credit"),
            toDouble(r, "capacite_remboursement"),
            toDouble(r, "indice_resilience"),
            toDouble(r, "est_producteur"),
            toDouble(r, "prix_produit_principal_moy"),
            toDouble(r, "volatilite_prix_produit"),
            toDouble(r, "tendance_prix_30j"),
            toDouble(r, "inflation_mensuelle_moy"),
            toDouble(r, "taux_directeur_beac"),
            toDouble(r, "precipitation_moy_mm"),
            toDouble(r, "indice_secheresse"),
            toDouble(r, "nb_evenements_negatifs")
        );
    }

    private void upsertScore(ScoringResultDto r, Long imfId) {
        jdbc.update(SQL_UPSERT,
            r.clientIdExterne(), imfId,
            r.scoreCrs(), r.scoreRps(), r.scoreCsi(), r.scoreMcrs(),
            r.classeRisque(), r.probabiliteDefaut30j(), r.probabiliteDefaut90j(),
            r.scoreMcrsIcBas(), r.scoreMcrsIcHaut(),
            r.actionRecommandee(), r.prioriteRecouvrement()
        );
    }

    private void pushSseNotification(String agentUsername, List<Map<String, Object>> scored) {
        SseEventDto event = SseEventDto.scoringUpdate(agentUsername, scored.size(), scored);
        // Notification ciblée à l'agent (s'il est connecté sur le web)
        sseRegistry.sendToUser(agentUsername, event);
        // Broadcast aux RESPONSABLE_RECOUVREMENT connectés sur le dashboard
        sseRegistry.broadcastToRole("RESPONSABLE_RECOUVREMENT", event);
    }

    private static Double toDouble(Map<String, Object> row, String col) {
        Object val = row.get(col);
        if (val == null) return null;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (NumberFormatException e) { return null; }
    }
}
