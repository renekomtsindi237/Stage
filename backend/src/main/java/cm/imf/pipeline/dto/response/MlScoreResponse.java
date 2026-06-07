package cm.imf.pipeline.dto.response;

import java.time.OffsetDateTime;

public record MlScoreResponse(
        String clientIdExterne,
        String imfCode,
        double scoreCrs,
        double scoreRps,
        double scoreCsi,
        double scoreMcrs,
        String classeRisque,
        double probabiliteDefaut30j,
        double probabiliteDefaut90j,
        double scoreMcrsIcBas,
        double scoreMcrsIcHaut,
        Integer tempsSurvieMedianJours,
        String actionRecommandee,
        int prioriteRecouvrement,
        OffsetDateTime scoredAt
) {}
