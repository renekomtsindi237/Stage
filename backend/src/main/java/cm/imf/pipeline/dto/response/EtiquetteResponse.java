package cm.imf.pipeline.dto.response;

import java.time.OffsetDateTime;

public record EtiquetteResponse(
        String uid,
        String dossierRef,
        String dossierType,
        String codeEtiquette,
        String couleur,
        String libelleCustom,
        String commentaire,
        boolean active,
        OffsetDateTime dateDebut,
        OffsetDateTime dateFin,
        String poseParUsername,
        String retireParUsername,
        OffsetDateTime dateRetrait,
        OffsetDateTime createdAt
) {}
