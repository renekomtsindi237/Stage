package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.response.AgentResponse;

import java.util.List;

/**
 * Contrat du service de consultation des agents terrain.
 * Lecture depuis le schéma staging via JdbcTemplate.
 */
public interface IAgentService {

    /**
     * Liste des agents d'une agence donnée.
     */
    List<AgentResponse> listByAgence(String idAgence);

    /**
     * Tous les agents de toutes les agences, paginés.
     */
    List<AgentResponse> listAll(int page, int size);

    /**
     * Nombre total d'agents.
     */
    long count();

    /**
     * Détail d'un agent par son identifiant métier.
     *
     * @throws cm.imf.pipeline.exception.ResourceNotFoundException si non trouvé
     */
    AgentResponse getById(String idAgent);

    /**
     * Recherche d'agents par nom (autocomplete).
     */
    List<AgentResponse> search(String query, int limit);
}
