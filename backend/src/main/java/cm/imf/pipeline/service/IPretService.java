package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.response.PretResponse;

import java.util.List;

/**
 * Contrat du service de consultation des prêts.
 * Lecture seule depuis le schéma staging via JdbcTemplate.
 */
public interface IPretService {

    /**
     * Liste paginée des prêts, filtrée par statut optionnel.
     * Triée par jours de retard décroissant.
     */
    List<PretResponse> listPrets(String statut, int page, int size);

    /**
     * Nombre total de prêts — utilisé pour la pagination.
     */
    long countPrets(String statut);

    /**
     * Détail d'un prêt par son identifiant métier.
     *
     * @throws cm.imf.pipeline.exception.ResourceNotFoundException si non trouvé
     */
    PretResponse getById(String idPret);

    /**
     * Tous les prêts actifs d'un client (fiche client).
     */
    List<PretResponse> getPretsClient(String idClient);

    /**
     * Prêts gérés par un agent (app mobile — sélection prêt pour collecte).
     */
    List<PretResponse> getPretsAgent(String nomAgent);
}
