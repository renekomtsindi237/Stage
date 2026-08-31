package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.CollecteRequest;
import cm.imf.pipeline.dto.request.SyncRequest;
import cm.imf.pipeline.dto.response.*;
import cm.imf.pipeline.entity.CollecteTerrain;
import cm.imf.pipeline.entity.SyncLog;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.StatutCollecte;
import cm.imf.pipeline.event.SyncCompletedEvent;
import cm.imf.pipeline.i18n.SyncMessages;
import cm.imf.pipeline.repository.CollecteRepository;
import cm.imf.pipeline.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service de synchronisation en lot pour l'app mobile Flutter.
 *
 * Workflow :
 *   1. Vérifie l'idempotence (syncId déjà traité → retourne le résultat précédent)
 *   2. Crée un SyncLog "EN_COURS"
 *   3. Traite chaque collecte indépendamment avec résolution de conflits
 *   4. Met à jour le SyncLog avec les statistiques finales
 *   5. Publie un SyncCompletedEvent pour notification SSE
 *
 * Garanties :
 *   - Idempotent : un même syncId ne produit jamais deux insertions
 *   - Atomique par item : l'échec d'un item n'annule pas les autres
 *   - Messages explicites : chaque résultat contient un message lisible par l'agent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollecteSyncService implements ICollecteSyncService {

    private final CollecteRepository      collecteRepository;
    private final SyncLogRepository       syncLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** Optionnel : isolé pour les tests unitaires (Mockito n'injecte pas toujours le TM). */
    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

    /**
     * Traite un batch de synchronisation.
     * Chaque item est traité dans sa propre transaction pour l'isolation des erreurs.
     */
    @Transactional
    public SyncResponse processSync(SyncRequest request, User agent, String ipClient) {
        log.info("Sync démarrée — syncId: {}, agent: {}, device: {}, {} item(s)",
                request.syncId(), agent.getUsername(), request.deviceId(),
                request.items().size());

        // ── Idempotence : syncId déjà traité ─────────────────────────────────
        if (syncLogRepository.existsBySyncId(request.syncId())) {
            log.warn("Sync déjà traitée (idempotence) — syncId: {}", request.syncId());
            SyncLog existing = syncLogRepository.findBySyncId(request.syncId()).orElseThrow();
            return buildIdempotentResponse(existing);
        }

        // ── Création du SyncLog ───────────────────────────────────────────────
        SyncLog syncLog = SyncLog.builder()
                .syncId(request.syncId())
                .deviceId(request.deviceId())
                .agent(agent)
                .nbItemsSoumis(request.items().size())
                .statutSync("EN_COURS")
                .ipClient(ipClient)
                .build();
        syncLogRepository.save(syncLog);

        // ── Traitement item par item ──────────────────────────────────────────
        List<SyncItemResult> resultats = new ArrayList<>();
        for (CollecteRequest item : request.items()) {
            SyncItemResult result = processOneItem(item, agent, request.syncId(),
                    request.deviceId());
            resultats.add(result);
        }

        // ── Mise à jour du SyncLog ────────────────────────────────────────────
        SyncResponse.SyncStats stats = SyncResponse.SyncStats.compute(resultats);
        String messageResume = buildMessageResume(stats);
        String statutGlobal  = computeStatutSync(stats);

        syncLog.setNbSucces(stats.succes());
        syncLog.setNbDoublons(stats.doublons());
        syncLog.setNbConflits(stats.conflits());
        syncLog.setNbErreurs(stats.erreurs());
        syncLog.setStatutSync(statutGlobal);
        syncLog.setMessageSync(messageResume);
        syncLog.setSyncCompletedAt(OffsetDateTime.now());
        syncLogRepository.save(syncLog);

        SyncResponse response = SyncResponse.of(request.syncId(), resultats, messageResume);

        // ── Collecte des clientIds nouvellement insérés (pour scoring temps réel) ──
        List<String> clientIds = new ArrayList<>();
        for (int i = 0; i < request.items().size(); i++) {
            if (SyncItemResult.CODE_SUCCESS.equals(resultats.get(i).code())) {
                String cid = request.items().get(i).clientId();
                if (cid != null && !cid.isBlank()) clientIds.add(cid);
            }
        }

        // ── Publication de l'événement SSE + scoring (ne doit jamais 500 la sync) ──
        try {
            Long imfId = agent.getImf() != null ? agent.getImf().getId() : null;
            eventPublisher.publishEvent(
                    new SyncCompletedEvent(this, response, agent.getUsername(), clientIds, imfId));
        } catch (Exception e) {
            log.warn("Événement post-sync ignoré : {}", e.getMessage());
        }

        log.info("Sync terminée — syncId: {}, statut: {}, succes: {}/{}, conflits: {}",
                request.syncId(), statutGlobal, stats.succes(), stats.total(), stats.conflits());

        return response;
    }

    /**
     * Traite un seul item de collecte avec résolution de conflits complète.
     * Chaque cas produit un message clair et un code exploitable par l'app mobile.
     */
    private SyncItemResult processOneItem(CollecteRequest item, User agent,
                                           String syncId, String deviceId) {
        if (transactionManager == null) {
            return processOneItemInternal(item, agent);
        }
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            return tt.execute(status -> processOneItemInternal(item, agent));
        } catch (Exception e) {
            log.error("Erreur traitement item sync {} : {}", item.idCollecteMobile(), e.getMessage(), e);
            return SyncItemResult.erreur(
                    item.idCollecteMobile(),
                    SyncMessages.erreurTechnique(e.getClass().getSimpleName()));
        }
    }

    private SyncItemResult processOneItemInternal(CollecteRequest item, User agent) {
        try {
            // ── Cas 1 : doublon par ID mobile (même appareil, réenvoi) ────────
            if (collecteRepository.existsByIdCollecteMobile(item.idCollecteMobile())) {
                CollecteTerrain existing = collecteRepository
                        .findByIdCollecteMobile(item.idCollecteMobile()).orElseThrow();
                log.debug("Doublon ID mobile détecté : {}", item.idCollecteMobile());
                return SyncItemResult.doublon(
                        item.idCollecteMobile(),
                        existing.getId(),
                        SyncMessages.COLLECTE_DOUBLON_ID);
            }

            // ── Cas 2 : doublon par référence transaction ─────────────────────
            if (item.referenceTransaction() != null
                    && collecteRepository.existsByReferenceTransactionAndDateCollecte(
                            item.referenceTransaction(), item.dateCollecte())) {
                log.warn("Doublon référence transaction : {} / {}",
                        item.referenceTransaction(), item.dateCollecte());
                return SyncItemResult.conflit(
                        item.idCollecteMobile(),
                        SyncMessages.COLLECTE_DOUBLON_REFERENCE);
            }

            // ── Cas 3 : date de collecte dans le futur ────────────────────────
            if (item.dateCollecte().isAfter(java.time.LocalDate.now())) {
                return SyncItemResult.conflit(
                        item.idCollecteMobile(),
                        SyncMessages.VALIDATION_DATE_FUTURE);
            }

            // ── Cas 4 : montant nul ou négatif ────────────────────────────────
            if (item.montantCollecte() == null ||
                    item.montantCollecte().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                return SyncItemResult.conflit(
                        item.idCollecteMobile(),
                        SyncMessages.VALIDATION_MONTANT_NEGATIF);
            }

            // ── Cas 5 : enregistrement nominal ───────────────────────────────
            String pretId = (item.pretId() == null || item.pretId().isBlank())
                    ? "SANS_PRET" : item.pretId();
            CollecteTerrain collecte = CollecteTerrain.builder()
                    .idCollecteMobile(item.idCollecteMobile())
                    .agent(agent)
                    .imf(agent.getImf())
                    .clientId(item.clientId())
                    .pretId(pretId)
                    .dateCollecte(item.dateCollecte())
                    .montantCollecte(item.montantCollecte())
                    .canalPaiement(item.canalPaiement())
                    .referenceTransaction(item.referenceTransaction())
                    .observation(item.observation())
                    .statut(StatutCollecte.CONFIRMEE)
                    .latitude(item.latitude())
                    .longitude(item.longitude())
                    .build();

            CollecteTerrain saved = collecteRepository.save(collecte);
            log.debug("Collecte enregistrée via sync : id={}, mobile={}",
                    saved.getId(), item.idCollecteMobile());

            return SyncItemResult.succes(
                    item.idCollecteMobile(),
                    saved.getId(),
                    SyncMessages.COLLECTE_CONFIRMEE);

        } catch (Exception e) {
            log.error("Erreur traitement item sync {} : {}", item.idCollecteMobile(), e.getMessage(), e);
            return SyncItemResult.erreur(
                    item.idCollecteMobile(),
                    SyncMessages.erreurTechnique(e.getClass().getSimpleName()));
        }
    }

    /**
     * Retourne le statut d'une synchronisation pour un appareil donné.
     */
    @Transactional(readOnly = true)
    public SyncStatusResponse getSyncStatus(String deviceId) {
        List<SyncLog> logs = syncLogRepository.findByDeviceIdOrderBySyncStartedAtDesc(deviceId);

        if (logs.isEmpty()) {
            return new SyncStatusResponse(deviceId, null, 0, 0, 0,
                    "Aucune synchronisation enregistrée pour cet appareil.");
        }

        SyncLog derniere = logs.get(0);
        int totalSucces   = syncLogRepository.sumSuccesByDeviceId(deviceId);
        int conflitsOuverts = syncLogRepository.sumConflitsOuvertsByDeviceId(deviceId);

        String message = conflitsOuverts > 0
                ? String.format("%d conflit(s) en attente de résolution. " +
                        "Dernière sync : %s.", conflitsOuverts,
                        derniere.getSyncCompletedAt() != null
                                ? derniere.getSyncCompletedAt().toLocalDate() : "en cours")
                : "Toutes les collectes sont synchronisées.";

        return new SyncStatusResponse(
                deviceId,
                derniere.getSyncCompletedAt(),
                logs.size(),
                totalSucces,
                conflitsOuverts,
                message);
    }

    // ── Helpers privés ────────────────────────────────────────────────────────

    private String buildMessageResume(SyncResponse.SyncStats stats) {
        int echecs = stats.conflits() + stats.erreurs();
        if (echecs == 0) return SyncMessages.SYNC_COMPLETE;
        if (echecs == stats.total()) return SyncMessages.SYNC_ECHEC;
        return SyncMessages.syncPartielle(
                stats.succes() + stats.doublons(),
                stats.total(),
                stats.conflits(),
                stats.erreurs());
    }

    private String computeStatutSync(SyncResponse.SyncStats stats) {
        int echecs = stats.conflits() + stats.erreurs();
        if (echecs == 0) return "COMPLETE";
        if (echecs == stats.total()) return "ECHEC";
        return "PARTIELLE";
    }

    private SyncResponse buildIdempotentResponse(SyncLog existing) {
        String message = "Synchronisation déjà traitée le " +
                (existing.getSyncCompletedAt() != null
                        ? existing.getSyncCompletedAt().toLocalDate()
                        : "aujourd'hui") +
                ". Aucune action supplémentaire nécessaire.";

        SyncResponse.SyncStats stats = new SyncResponse.SyncStats(
                existing.getNbItemsSoumis(), existing.getNbSucces(), existing.getNbDoublons(),
                existing.getNbConflits(), existing.getNbErreurs(), 0);

        return new SyncResponse(existing.getSyncId(), existing.getSyncCompletedAt(),
                SyncResponse.StatutGlobal.COMPLETE, message, stats, List.of());
    }
}
