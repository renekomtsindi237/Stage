package cm.imf.pipeline.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ChefAgenceDashboardResponse(
        long agentsCount,
        long clientsCount,
        long collectesJour,
        double par30,
        long dossiersEnAttente,
        long dossiersValidesMois,
        List<DossierPendant> dossiers
) {
    public record DossierPendant(
            String uid,
            String clientNom,
            String clientId,
            BigDecimal montantDemande,
            Integer dureeMois,
            String secteurActivite,
            String objetFinancement,
            String agentNom,
            OffsetDateTime dateSoumission,
            String statut,
            String noteAnalyse
    ) {}
}
