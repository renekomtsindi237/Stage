package cm.imf.pipeline.service;

import cm.imf.pipeline.entity.JournalAudit;
import cm.imf.pipeline.entity.User;
import org.springframework.data.domain.Page;

/**
 * Contrat du service de journalisation des actions (audit trail RGPD).
 * Toutes les méthodes d'écriture sont asynchrones.
 */
public interface IAuditService {

    /**
     * Enregistre une action réussie dans le journal d'audit.
     *
     * @param user     utilisateur ayant effectué l'action (peut être null pour les actions système)
     * @param action   code de l'action (ex: ALERTE_CLOTUREE, LOGIN, COLLECTE_SOUMISE)
     * @param entite   nom de l'entité concernée (ex: AlerteImpaye, CollecteTerrain)
     * @param entiteId identifiant de l'entité concernée
     * @param details  informations complémentaires (JSON ou texte libre)
     * @param ipClient adresse IP du client
     */
    void log(User user, String action, String entite, String entiteId,
             String details, String ipClient);

    /**
     * Enregistre un échec ou un refus d'accès.
     */
    void logEchec(String username, String action, String details, String ipClient);

    /**
     * Historique paginé des actions d'un utilisateur.
     */
    Page<JournalAudit> getHistorique(String username, int page, int size);

    /**
     * Historique paginé des actions d'un type donné.
     */
    Page<JournalAudit> getByAction(String action, int page, int size);
}
