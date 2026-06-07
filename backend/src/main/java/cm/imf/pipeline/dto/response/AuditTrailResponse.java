package cm.imf.pipeline.dto.response;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Projection de app.audit_trail pour l'API.
 * Les champs ancienneValeur / nouvelleValeur sont masqués selon le rôle
 * par DataMaskingUtils avant la sérialisation.
 */
public record AuditTrailResponse(
        Long   id,
        Long   imfId,
        Long   acteurId,
        String acteurUsername,
        String acteurRole,
        String action,
        String entiteType,
        String entiteId,
        Map<String, Object> ancienneValeur,
        Map<String, Object> nouvelleValeur,
        String motif,
        String ipClient,
        String statut,
        OffsetDateTime createdAt
) {}
