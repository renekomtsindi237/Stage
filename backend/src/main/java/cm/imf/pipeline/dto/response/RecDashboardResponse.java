package cm.imf.pipeline.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record RecDashboardResponse(
        long creancesActives,
        BigDecimal montantEnRetard,
        long actionsDuMois,
        double tauxRecouvrement,
        List<CreanceItem> creances,
        Map<String, Long> parPhase
) {
    public record CreanceItem(
            String id,
            String clientNom,
            BigDecimal montant,
            int joursRetard,
            String statut,
            String phase
    ) {}
}
