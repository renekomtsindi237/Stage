package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.Agence;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public record AgenceResponse(
        @JsonProperty("id") String uid,
        String nom,
        String ville,
        String responsable,
        String telephone,
        boolean actif,
        OffsetDateTime createdAt,
        long agentsCount,
        long clientsCount,
        long encoursFcfa,
        double par30
) {
    public static AgenceResponse from(Agence a) {
        return new AgenceResponse(
                a.getUid() != null ? a.getUid().toString() : null,
                a.getNom(), a.getVille(),
                a.getResponsable(), a.getTelephone(),
                a.isActif(), a.getCreatedAt(),
                0L, 0L, 0L, 0.0
        );
    }
}
