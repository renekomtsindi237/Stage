package cm.imf.pipeline.enums;

/**
 * Phases du workflow de recouvrement des créances (contexte OHADA/COBAC Cameroun).
 *
 * RELANCE_AMIABLE  — J+1  à J+30  : SMS, appels, visites terrain par l'agent
 * MEDIATION_AMIABLE— J+31 à J+89  : Médiation chef de quartier/famille (étape culturelle locale)
 * MISE_EN_DEMEURE  — J+90 à J+179 : Lettre recommandée formelle via huissier (OHADA art. 110)
 * CONTENTIEUX      — J+180+        : Voie judiciaire, saisie-attribution, tribunal de commerce
 * REECHELONNEMENT  — À tout moment : Accord de rééchelonnement formel validé par comité de crédit
 * PERTE            — Radiation comptable : créance irrécouvrable (provisionnement 100 % COBAC)
 */
public enum RecouvrementPhase {
    RELANCE_AMIABLE,
    MEDIATION_AMIABLE,
    MISE_EN_DEMEURE,
    CONTENTIEUX,
    REECHELONNEMENT,
    PERTE
}
