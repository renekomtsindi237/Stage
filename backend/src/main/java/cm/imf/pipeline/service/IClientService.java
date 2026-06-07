package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.response.ClientResponse;

import java.util.List;

/**
 * Contrat du service de consultation des clients.
 * Lecture seule depuis le schéma staging via JdbcTemplate.
 */
public interface IClientService {

    /**
     * Recherche de clients par nom ou téléphone (autocomplete web + mobile).
     */
    List<ClientResponse> search(String query, int limit);

    /**
     * Détail d'un client par son identifiant métier.
     *
     * @throws cm.imf.pipeline.exception.ResourceNotFoundException si non trouvé
     */
    ClientResponse getById(String idClient);

    /**
     * Liste paginée de tous les clients.
     */
    List<ClientResponse> list(int page, int size);

    /**
     * Nombre total de clients — utilisé pour la pagination.
     */
    long count();
}
