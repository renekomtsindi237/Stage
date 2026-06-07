package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.AlerteUpdateRequest;
import cm.imf.pipeline.dto.response.AlerteResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.enums.StatutAlerte;

import java.util.UUID;

/**
 * Contrat du service de gestion des alertes impayés.
 */
public interface IAlertService {

    /**
     * Liste paginée des alertes, filtrée par statut optionnel.
     * Triée par joursRetard décroissant.
     */
    PageResponse<AlerteResponse> getAlertes(StatutAlerte statut, int page, int size);

    /**
     * Détail d'une alerte par ID.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 si non trouvée
     */
    AlerteResponse getById(UUID uid);

    /**
     * Mise à jour du statut d'une alerte (clôturer / escalader).
     * Valide les transitions d'état autorisées.
     *
     * @throws org.springframework.web.server.ResponseStatusException 422 si alerte déjà clôturée
     */
    AlerteResponse updateStatut(UUID uid, AlerteUpdateRequest request);

    /**
     * Nombre d'alertes actives — utilisé par le tableau de bord KPI.
     */
    long countActiveAlertes();
}
