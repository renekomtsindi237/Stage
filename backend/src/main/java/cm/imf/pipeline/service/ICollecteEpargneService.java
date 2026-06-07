package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.CollecteEpargneRequest;
import cm.imf.pipeline.dto.request.SyncCollectesRequest;
import cm.imf.pipeline.dto.response.CollecteEpargneResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.dto.response.SyncCollectesResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ICollecteEpargneService {

    /** Soumission d'une collecte depuis l'app mobile (ou web). */
    CollecteEpargneResponse soumettre(CollecteEpargneRequest request);

    /** Synchronisation batch depuis l'app Flutter (offline-first). */
    SyncCollectesResponse syncBatch(SyncCollectesRequest request);

    /** Validation/rejet par un superviseur. */
    CollecteEpargneResponse valider(UUID uid, String motifRejet);

    /** Récupération paginée pour dashboard. */
    PageResponse<CollecteEpargneResponse> lister(
            Long imfId, Long agenceId, Long agentId,
            LocalDate dateDebut, LocalDate dateFin,
            String statut, int page, int size);

    /** Collectes en attente de synchronisation pour un agent. */
    List<CollecteEpargneResponse> collectesNonSynchros(Long agentId);

    /** KPI rapide : montant total et nb collectes du jour pour l'agent connecté. */
    CollecteEpargneResponse.KpiJour kpiJour(Long agentId, LocalDate date);
}
