package cm.imf.pipeline.scheduler;

import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.repository.OtpCodeRepository;
import cm.imf.pipeline.repository.RefreshTokenRepository;
import cm.imf.pipeline.service.INotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Tâches planifiées du système IMF Pipeline.
 *
 * Maintenance :
 *   - Nettoyage refresh tokens expirés (toutes les heures)
 *   - Invalidation caches KPI (02h00 — après DAGs Airflow nocturnes)
 *   - Purge piste d'audit — rétention 5 ans art. 13 Loi 2024/017 (1er du mois 03h00)
 *   - Purge positions GPS — TTL 90 jours RGPD (dimanches 04h00)
 *
 * Conformité légale :
 *   - SLA RGPD : détection demandes dépassant 30j → EN_RETARD + alerte DSI (01h00 quotidien)
 *   - Violation données : alerte 72h art. 22 Loi 2024/017 (toutes les 4h)
 *   - KYC suspendus : expiration 30j → rejet automatique (02h30 quotidien)
 *
 *  Pipeline ML :
 *   - Éviction caches scores ML (08h30 — après scoring DAG 07h30)
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class ScheduledTasks {

    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpCodeRepository      otpCodeRepository;
    private final CacheManager           cacheManager;
    private final JdbcTemplate           jdbc;
    private final INotificationService   notificationService;

    // ══════════════════════════════════════════════════════════════════════════
    // MAINTENANCE TECHNIQUE
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    @Scheduled(fixedRateString = "${imf.pipeline.scheduler.token-cleanup-ms:3600000}")
    public void cleanupExpiredTokens() {
        int deleted = refreshTokenRepository.deleteByExpiresAtBefore(OffsetDateTime.now());
        if (deleted > 0) {
            log.info("Nettoyage refresh tokens : {} token(s) expiré(s) supprimé(s)", deleted);
        }
        otpCodeRepository.deleteExpired(OffsetDateTime.now());
    }

    @Scheduled(cron = "${imf.pipeline.scheduler.cache-evict-cron:0 0 2 * * *}")
    public void evictKpiCaches() {
        evictIfPresent("kpi-par");
        evictIfPresent("kpi-collectes");
        evictIfPresent("kpi-dashboard");
        evictIfPresent("prets-list");
        evictIfPresent("prets-agent");
        evictIfPresent("clients-search");
        evictIfPresent("agents-agence");
        evictIfPresent("agents-list");
        evictIfPresent("agents-search");
        log.info("Caches KPI/prêts/clients/agents invalidés (refresh nocturne après DAGs Airflow)");
    }

    @Scheduled(cron = "${imf.pipeline.scheduler.audit-purge-cron:0 0 3 1 * *}")
    public void purgerAuditTrailAncien() {
        try {
            jdbc.execute("CALL app.purger_audit_trail_ancien(5)");
            log.info("Purge audit_trail exécutée (rétention 5 ans — art. 13 Loi 2024/017)");
        } catch (Exception e) {
            log.error("Échec purge audit_trail : {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${imf.pipeline.scheduler.positions-purge-cron:0 0 4 * * SUN}")
    public void purgerPositionsAnciennes() {
        try {
            jdbc.execute("CALL app.purger_positions_anciennes(90)");
            log.info("Purge positions GPS exécutée (rétention 90 jours)");
        } catch (Exception e) {
            log.error("Échec purge positions GPS : {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CONFORMITÉ RGPD — LOI 2024/017 CAMEROUN
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Détecte les demandes RGPD dont le délai légal de 30 jours est dépassé (art. 41).
     * Passe leur statut à EN_RETARD et alerte les DSI concernés.
     * Exécuté chaque nuit à 01h00.
     */
    @Transactional
    @Scheduled(cron = "${imf.pipeline.scheduler.rgpd-sla-cron:0 0 1 * * *}")
    public void detecterDemandesRgpdEnRetard() {
        try {
            int updated = jdbc.update(
                    "UPDATE app.demandes_rgpd " +
                    "SET statut = 'EN_RETARD', updated_at = NOW() " +
                    "WHERE statut IN ('SOUMISE', 'EN_ATTENTE', 'EN_COURS') " +
                    "  AND date_limite_reponse < NOW()");

            if (updated > 0) {
                log.warn("SLA RGPD : {} demande(s) passée(s) EN_RETARD (délai 30j dépassé — art. 41)", updated);
                notificationService.sendPushToRole(Role.DSI,
                        "⚠️ Demandes RGPD en retard",
                        updated + " demande(s) dépassent le délai légal de 30 jours — action requise (art. 41)");
            }
        } catch (Exception e) {
            log.error("Erreur vérification SLA RGPD : {}", e.getMessage());
        }
    }

    /**
     * Alerte pour les violations de données dont le délai de notification
     * à l'autorité approche ou est dépassé (art. 22 §1 — délai 72h).
     * Exécuté toutes les 4 heures.
     */
    @Scheduled(cron = "${imf.pipeline.scheduler.violation-72h-cron:0 0 */4 * * *}")
    public void alerterViolations72h() {
        try {
            List<Map<String, Object>> urgentes = jdbc.queryForList(
                    "SELECT uid::text AS uid, imf_id, " +
                    "       EXTRACT(EPOCH FROM (NOW() - date_decouverte)) / 3600 AS heures_ecoulees " +
                    "FROM app.violations_donnees " +
                    "WHERE statut = 'DECLAREE' " +
                    "  AND notif_autorite_envoyee = FALSE " +
                    "  AND date_decouverte < NOW() - INTERVAL '60 hours'");

            if (!urgentes.isEmpty()) {
                long depassees = urgentes.stream()
                        .filter(v -> ((Number) v.get("heures_ecoulees")).doubleValue() >= 72)
                        .count();
                String msg = String.format(
                        "%d violation(s) nécessitent une notification à l'autorité dans les prochaines heures. "
                        + "%d dépassent déjà les 72h légales (art. 22 §1).",
                        urgentes.size(), depassees);

                log.warn("ALERTE violations 72h : {}", msg);
                notificationService.sendPushToRole(Role.DSI, "🚨 Délai 72h violation données", msg);
            }
        } catch (Exception e) {
            log.error("Erreur alerte violations 72h : {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // KYC — EXPIRATION DES DOSSIERS SUSPENDUS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Rejette automatiquement les dossiers KYC en état SUSPENDU
     * depuis plus de 30 jours sans réponse du client.
     * Exécuté chaque nuit à 02h30.
     */
    @Transactional
    @Scheduled(cron = "${imf.pipeline.scheduler.kyc-suspension-cron:0 30 2 * * *}")
    public void expireKycSuspendus() {
        try {
            int rejected = jdbc.update(
                    "UPDATE app.kyc_dossiers " +
                    "SET statut = 'REJETE', updated_at = NOW() " +
                    "WHERE statut = 'SUSPENDU' " +
                    "  AND updated_at < NOW() - INTERVAL '30 days'");

            if (rejected > 0) {
                log.info("KYC : {} dossier(s) SUSPENDU(s) rejeté(s) automatiquement (délai 30j expiré)", rejected);
            }
        } catch (Exception e) {
            log.error("Erreur expiration dossiers KYC suspendus : {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PIPELINE ML — ÉVICTION CACHE POST-SCORING
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Invalide les caches liés aux scores ML après le passage du DAG de scoring (07h30).
     * Exécuté chaque matin à 08h30.
     */
    @Scheduled(cron = "${imf.pipeline.scheduler.ml-cache-evict-cron:0 30 8 * * *}")
    public void evictMlCachesPostScoring() {
        evictIfPresent("ml-scores");
        evictIfPresent("ml-alertes");
        evictIfPresent("ml-model-info");
        log.info("Caches ML invalidés (post-scoring DAG 07h30)");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private void evictIfPresent(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
