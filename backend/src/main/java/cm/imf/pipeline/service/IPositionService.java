package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.PositionRequest;
import cm.imf.pipeline.dto.response.AgentPositionResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Contrat du service de géolocalisation des agents terrain.
 *
 * Flux :
 *  1. L'app Flutter envoie un ping GPS périodique → mettreAJourPosition()
 *  2. Le backend upsert app.utilisateurs + insère dans app.positions_agents
 *  3. Un événement SSE est pushé aux responsables de l'IMF
 *  4. Les superviseurs appellent listerPositionsActives() pour la carte
 *  5. L'historique du trajet journalier est disponible via historiqueJournalier()
 */
public interface IPositionService {

    /**
     * Met à jour la position courante d'un agent et l'insère dans l'historique.
     *
     * @param agentId   ID de l'agent (extrait du JWT)
     * @param imfId     ID de l'IMF de l'agent
     * @param request   Données GPS du ping
     * @return          Position enrichie (nom, agence, statut)
     */
    AgentPositionResponse mettreAJourPosition(Long agentId, Long imfId, PositionRequest request);

    /**
     * Désactive le partage de position d'un agent (RGPD — droit d'opposition).
     * La dernière position est conservée mais marquée inactive.
     *
     * @param agentId   ID de l'agent
     * @param imfId     ID de l'IMF
     */
    void desactiverPartage(Long agentId, Long imfId);

    /**
     * Liste les positions actuelles de tous les agents actifs d'une IMF.
     * "Actif" = dernier ping < 15 minutes et partage activé.
     *
     * @param imfId     ID de l'IMF (multi-tenant)
     * @param agenceId  Filtre optionnel par agence (null = toutes)
     * @return          Liste des positions pour la carte
     */
    List<AgentPositionResponse> listerPositionsActives(Long imfId, Long agenceId);

    /**
     * Historique des positions d'un agent pour une date donnée (trajet journalier).
     * Limité aux 500 derniers points pour éviter les surcharges.
     *
     * @param agentId   ID de l'agent
     * @param imfId     ID de l'IMF (contrôle d'accès tenant)
     * @param date      Date du trajet (défaut = aujourd'hui)
     * @return          Liste chronologique des positions
     */
    List<AgentPositionResponse> historiqueJournalier(Long agentId, Long imfId, LocalDate date);
}
