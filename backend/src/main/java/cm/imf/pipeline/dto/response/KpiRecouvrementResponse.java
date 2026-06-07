package cm.imf.pipeline.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KpiRecouvrementResponse(
    String agenceUid,
    LocalDate datePeriode,
    // PAR
    BigDecimal par30Montant,
    BigDecimal par60Montant,
    BigDecimal par90Montant,
    double par30TauxPct,
    double par60TauxPct,
    double par90TauxPct,
    // Recouvrement
    double tauxRecouvrementPct,
    BigDecimal montantRecouvre,
    BigDecimal montantPerteNette,
    // Portefeuille
    BigDecimal encoursTotal,
    int nbCreancesActives,
    int nbCreancesProbleme,
    // Provisions
    BigDecimal totalProvisions,
    // Benchmark
    Integer rangAgence,
    Integer nbAgencesComparees
) {}
