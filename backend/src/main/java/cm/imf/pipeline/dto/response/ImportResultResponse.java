package cm.imf.pipeline.dto.response;

import java.util.List;

public record ImportResultResponse(
        int          totalLignes,
        int          importe,
        int          miseAJour,
        int          erreurs,
        List<String> lignesErreur
) {}
