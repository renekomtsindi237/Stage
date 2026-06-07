package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.EcheanceUpdateRequest;
import cm.imf.pipeline.dto.response.EcheanceResponse;
import cm.imf.pipeline.dto.response.PageResponse;

import java.util.List;
import java.util.UUID;

/**
 * Contrat du service de gestion des échéances de remboursement applicatives.
 * Gère le cycle de vie des échéances dans la table app.echeances_app.
 */
public interface IEcheanceService {

    /**
     * Toutes les échéances d'un prêt, ordonnées par numéro d'échéance croissant.
     */
    List<EcheanceResponse> getByPret(String idPret);

    /**
     * Détail d'une échéance par ID.
     *
     * @throws cm.imf.pipeline.exception.ResourceNotFoundException si non trouvée
     */
    EcheanceResponse getById(UUID uid);

    /**
     * Met à jour le statut et/ou le montant payé d'une échéance.
     * Valide les transitions : une échéance ANNULEE ne peut pas être modifiée.
     *
     * @throws cm.imf.pipeline.exception.ResourceNotFoundException si non trouvée
     * @throws org.springframework.web.server.ResponseStatusException 422 si ANNULEE
     */
    EcheanceResponse updateStatut(UUID uid, EcheanceUpdateRequest request);

    /**
     * Liste paginée des échéances en retard (statut EN_RETARD).
     * Utilisée par le tableau de bord recouvrement.
     */
    PageResponse<EcheanceResponse> getEcheancesEnRetard(int page, int size);

    /**
     * Nombre d'échéances en retard — utilisé par le dashboard KPI.
     */
    long countEnRetard();
}
