package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.CollecteRequest;
import cm.imf.pipeline.dto.response.CollecteResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.entity.User;

import java.util.UUID;

/**
 * Contrat du service de gestion des collectes terrain (saisie unitaire).
 */
public interface ICollecteService {

    /**
     * Enregistre une collecte terrain avec déduplication double :
     *   1. Par idCollecteMobile (UUID Flutter)
     *   2. Par referenceTransaction + dateCollecte
     */
    CollecteResponse enregistrer(CollecteRequest request, User agent);

    /**
     * Liste paginée des collectes de l'agent connecté, triée par date décroissante.
     */
    PageResponse<CollecteResponse> getMesCollectes(User agent, int page, int size);

    /**
     * Détail d'une collecte par uid public.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 si non trouvée
     */
    CollecteResponse getById(UUID uid);
}
