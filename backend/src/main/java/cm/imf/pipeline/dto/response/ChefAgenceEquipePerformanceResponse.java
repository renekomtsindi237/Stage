package cm.imf.pipeline.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ChefAgenceEquipePerformanceResponse(
        int jours,
        LocalDate debut,
        LocalDate fin,
        List<MembrePerformance> membres
) {
    public record MembrePerformance(
            String uid,
            String username,
            String role,
            String zoneId,
            boolean actif,
            long collectesCount,
            BigDecimal collectesMontant,
            long collectesCountPrec,
            BigDecimal collectesMontantPrec,
            double evolutionPct,
            String tendance,
            long dossiersSoumis,
            long dossiersValides,
            long dossiersRejetes,
            double tauxValidation,
            long clientsTouches
    ) {}
}
