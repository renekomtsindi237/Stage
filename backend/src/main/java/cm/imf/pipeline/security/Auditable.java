package cm.imf.pipeline.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marque une méthode de service ou de controller pour déclencher
 * l'enregistrement automatique dans la piste d'audit immuable (app.audit_trail).
 *
 * L'aspect AuditAspect intercepte les méthodes annotées et enregistre :
 *   - l'acteur (depuis SecurityContext)
 *   - l'action et le type d'entité
 *   - les valeurs avant/après si le paramètre captureResult est vrai
 *   - l'IP client depuis l'attribut de requête "clientIp"
 *
 * Exemple d'usage :
 * <pre>
 * {@literal @}Auditable(action = AuditTrail.ACTION_CHANGEMENT_STATUT,
 *             entiteType = AuditTrail.ENTITE_ALERTE)
 * public AlerteResponse changerStatut(Long id, AlerteUpdateRequest req) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** Code action — utiliser les constantes AuditTrail.ACTION_* */
    String action();

    /** Type d'entité — utiliser les constantes AuditTrail.ENTITE_* */
    String entiteType() default "";

    /**
     * Expression SpEL évaluée sur les paramètres de la méthode pour extraire l'entiteId.
     * Exemple : "#id" pour un paramètre nommé "id", "#req.dossierId" pour un objet.
     * Si vide, l'entiteId sera null dans l'audit.
     */
    String entiteIdExpression() default "";

    /**
     * Si true, le résultat de la méthode est sérialisé dans nouvelle_valeur.
     * Utile pour auditer les créations (le résultat contient l'entité créée).
     */
    boolean captureResult() default false;

    /**
     * Expression SpEL pour extraire le motif depuis les paramètres.
     * Exemple : "#req.motif"
     */
    String motifExpression() default "";

    /**
     * Expression SpEL évaluée AVANT l'appel de la méthode pour capturer l'état
     * de l'entité avant modification (ancienne_valeur dans la piste d'audit).
     *
     * Supporte les références aux beans Spring via @nomBean.
     * Exemples :
     *   "@alerteRepository.findById(#id).orElse(null)"
     *   "@echeanceService.getById(#id)"
     *
     * Si vide, ancienne_valeur sera null (acceptable pour CREATION et CONSULTATION).
     */
    String ancienneValeurExpression() default "";
}
