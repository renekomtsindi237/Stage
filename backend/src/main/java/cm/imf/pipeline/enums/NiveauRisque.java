package cm.imf.pipeline.enums;

/**
 * Scoring de risque LBC/FT conformément au Règlement COBAC R-2005/01.
 * Le score (0-100) est calculé par le service KYC à partir de critères pondérés.
 */
public enum NiveauRisque {
    FAIBLE(0, 25),      // Client identifié, activité connue, petites transactions locales
    MOYEN(26, 50),      // Activité commerciale, transactions transfrontalières modérées
    ELEVE(51, 75),      // PPE, transactions importantes, activité sensible
    TRES_ELEVE(76, 100);// Non-coopératif, pays tiers à risque élevé, transactions suspectes

    public final int scoreMin;
    public final int scoreMax;

    NiveauRisque(int min, int max) {
        this.scoreMin = min;
        this.scoreMax = max;
    }

    public static NiveauRisque of(int score) {
        for (NiveauRisque n : values()) {
            if (score >= n.scoreMin && score <= n.scoreMax) return n;
        }
        return TRES_ELEVE;
    }
}
