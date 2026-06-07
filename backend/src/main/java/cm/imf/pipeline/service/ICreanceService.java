package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.response.CreanceResponse;
import cm.imf.pipeline.dto.response.KpiRecouvrementResponse;
import cm.imf.pipeline.dto.response.PageResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface ICreanceService {

    /** Récupération paginée des créances avec filtres. */
    PageResponse<CreanceResponse> lister(
            Long imfId, Long agenceId, String categoriePar,
            String statut, LocalDate dateDebut, LocalDate dateFin,
            int page, int size);

    /** Détail d'une créance avec historique actions recouvrement. */
    CreanceResponse detail(UUID uid);

    /** KPI recouvrement : PAR30/60/90, taux recouvrement, provisions. */
    KpiRecouvrementResponse kpiRecouvrement(Long imfId, UUID agenceUid, LocalDate datePeriode);

    /** Score MCRS du client (dernière valeur disponible dans ml.client_scores). */
    CreanceResponse.ScoreMcrs scoreClient(Long imfId, String clientIdExterne);

    /** Mise à jour statut créance (ex: SOLDEE, IRRECOVERABLE). */
    CreanceResponse majStatut(UUID uid, String nouveauStatut, String observation);
}
