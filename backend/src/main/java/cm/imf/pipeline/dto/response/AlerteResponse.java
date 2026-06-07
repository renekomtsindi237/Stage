package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.AlerteImpaye;
import cm.imf.pipeline.enums.StatutAlerte;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AlerteResponse(
        String uid,
        String idPret,
        OffsetDateTime dateGeneration,
        int joursRetard,
        BigDecimal montantEnRetard,
        StatutAlerte statutAlerte,
        boolean fcmSent,
        boolean emailSent,
        OffsetDateTime dateCloture
) {
    public static AlerteResponse from(AlerteImpaye a) {
        return new AlerteResponse(
                a.getUid() != null ? a.getUid().toString() : null,
                a.getIdPret(),
                a.getDateGeneration(),
                a.getJoursRetard(),
                a.getMontantEnRetard(),
                a.getStatutAlerte(),
                a.isFcmSent(),
                a.isEmailSent(),
                a.getDateCloture()
        );
    }
}
