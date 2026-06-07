package cm.imf.pipeline.enums;

import java.math.BigDecimal;

/**
 * Classification réglementaire COBAC des créances en souffrance.
 * Détermine le taux de provisionnement obligatoire pour le reporting mensuel.
 *
 * Source : Règlement COBAC R-2010/01 relatif aux conditions d'exercice
 *          et de contrôle de l'activité de microfinance en Afrique Centrale.
 */
public enum CategorieCobtac {

    /** J+1  à J+30  — Provisionnement : 5 % */
    EN_SURVEILLANCE(new BigDecimal("5.00")),

    /** J+31 à J+90  — Provisionnement : 25 % */
    DOUTEUSE(new BigDecimal("25.00")),

    /** J+91 à J+180 — Provisionnement : 50 % */
    LITIGIEUSE(new BigDecimal("50.00")),

    /** J+181+        — Provisionnement : 100 % */
    CONTENTIEUSE(new BigDecimal("100.00"));

    private final BigDecimal tauxProvision;

    CategorieCobtac(BigDecimal tauxProvision) {
        this.tauxProvision = tauxProvision;
    }

    public BigDecimal getTauxProvision() {
        return tauxProvision;
    }

    /** Détermine la catégorie COBAC à partir du nombre de jours de retard. */
    public static CategorieCobtac of(int joursRetard) {
        if (joursRetard > 180) return CONTENTIEUSE;
        if (joursRetard >  90) return LITIGIEUSE;
        if (joursRetard >  30) return DOUTEUSE;
        return EN_SURVEILLANCE;
    }
}
