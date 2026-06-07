package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.response.AuditTrailResponse;
import cm.imf.pipeline.entity.AuditTrail;
import org.springframework.data.domain.Page;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Service de piste d'audit immuable — art. 27 Loi 2024/017 Cameroun.
 * Les méthodes d'écriture sont asynchrones pour ne pas bloquer la transaction métier.
 * La lecture est synchrone et applique le masquage PII selon le rôle du demandeur.
 */
public interface IAuditTrailService {

    /**
     * Enregistre une entrée d'audit avec old/new values.
     *
     * @param action         code action (AuditTrail.ACTION_*)
     * @param entiteType     type d'entité (AuditTrail.ENTITE_*)
     * @param entiteId       identifiant de l'entité
     * @param ancienneValeur état avant modification (null si CREATION)
     * @param nouvelleValeur état après modification (null si SUPPRESSION)
     * @param motif          justification saisie par l'acteur
     * @param ipClient       adresse IP de la requête
     * @param userAgent      User-Agent HTTP
     */
    void enregistrer(String action, String entiteType, String entiteId,
                     Map<String, Object> ancienneValeur, Map<String, Object> nouvelleValeur,
                     String motif, String ipClient, String userAgent);

    /** Enregistre un échec ou un accès refusé. */
    void enregistrerEchec(String action, String entiteType, String entiteId,
                          String motif, String ipClient);

    /**
     * Recherche filtrée paginée de la piste d'audit (DSI / SUPER_ADMIN).
     * Les valeurs PII sont masquées selon le rôle de l'appelant.
     */
    Page<AuditTrailResponse> rechercher(Long imfId, String entiteType, String entiteId,
                                         String action, String username,
                                         OffsetDateTime debut, OffsetDateTime fin,
                                         int page, int size);

    /** Historique complet d'une entité spécifique (ex: tous les accès à un dossier). */
    Page<AuditTrailResponse> historiqueEntite(Long imfId, String entiteType,
                                               String entiteId, int page, int size);
}
