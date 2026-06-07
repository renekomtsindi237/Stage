package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.Agence;

import java.time.OffsetDateTime;

public record AgenceResponse(
        String uid,
        String nom,
        String ville,
        String responsable,
        String telephone,
        boolean actif,
        OffsetDateTime createdAt
) {
    public static AgenceResponse from(Agence a) {
        return new AgenceResponse(
                a.getUid() != null ? a.getUid().toString() : null,
                a.getNom(), a.getVille(),
                a.getResponsable(), a.getTelephone(),
                a.isActif(), a.getCreatedAt()
        );
    }
}
