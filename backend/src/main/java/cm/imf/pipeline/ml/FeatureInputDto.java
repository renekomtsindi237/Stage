package cm.imf.pipeline.ml;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO Java miroir du schéma Pydantic FeatureInput de l'API FastAPI MCRS.
 * Toutes les features numériques sont nullable — FastAPI impute les null
 * avec les médianes sectorielles (FEATURE_DEFAULTS dans mcrs_model.py).
 *
 * @JsonInclude(NON_NULL) évite d'envoyer "nb_collectes_12m": null dans le JSON
 * et laisse FastAPI appliquer ses propres défauts.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeatureInputDto(

    @JsonProperty("client_id_externe")  String clientIdExterne,
    @JsonProperty("imf_code")           String imfCode,
    @JsonProperty("region_id")          String regionId,
    @JsonProperty("region_name")        String regionName,

    // ── CRS — Collection Reliability Score ────────────────────────────────────
    @JsonProperty("nb_collectes_12m")             Double nbCollectes12m,
    @JsonProperty("regularite_collecte_pct")      Double regulariteCollectePct,
    @JsonProperty("tendance_collecte_3m")         Double tendanceCollecte3m,
    @JsonProperty("montant_moy_collecte")         Double montantMoyCollecte,
    @JsonProperty("ecart_type_collecte")          Double ecartTypeCollecte,
    @JsonProperty("nb_cycles_manques_12m")        Double nbCyclesManques12m,
    @JsonProperty("montant_total_collectes_12m")  Double montantTotalCollectes12m,

    // ── RPS — Recovery Prediction Score ───────────────────────────────────────
    @JsonProperty("taux_remboursement_pct")       Double tauxRemboursementPct,
    @JsonProperty("jours_retard_moyen")           Double joursRetardMoyen,
    @JsonProperty("jours_retard_max")             Double joursRetardMax,
    @JsonProperty("nb_incidents_paiement")        Double nbIncidentsPaiement,
    @JsonProperty("montant_impaye_courant")       Double montantImPayeCourant,
    @JsonProperty("nb_remboursements_12m")        Double nbRemboursements12m,
    @JsonProperty("classe_risque_cobac_encode")   Double classeRisqueCobacEncode,

    // ── CSI — Client Solvency Index ────────────────────────────────────────────
    @JsonProperty("revenu_mensuel_estime")        Double revenuMensuelEstime,
    @JsonProperty("anciennete_client_jours")      Double ancienneteClientJours,
    @JsonProperty("nb_produits_actifs")           Double nbProduitsActifs,
    @JsonProperty("ratio_collecte_credit")        Double ratioCollecteCredit,
    @JsonProperty("capacite_remboursement")       Double capaciteRemboursement,
    @JsonProperty("indice_resilience")            Double indiceResilience,
    @JsonProperty("est_producteur")               Double estProducteur,
    @JsonProperty("prix_produit_principal_moy")   Double prixProduitPrincipalMoy,
    @JsonProperty("volatilite_prix_produit")      Double volatilitePrixProduit,
    @JsonProperty("tendance_prix_30j")            Double tendancePrix30j,
    @JsonProperty("inflation_mensuelle_moy")      Double inflationMensuelleMoy,
    @JsonProperty("taux_directeur_beac")          Double tauxDirecteurBeac,
    @JsonProperty("precipitation_moy_mm")         Double precipitationMoyMm,
    @JsonProperty("indice_secheresse")            Double indiceSecheresse,
    @JsonProperty("nb_evenements_negatifs")       Double nbEvenementsNegatifs

) {
    /** Construit un FeatureInputDto minimal — FastAPI imputera toutes les features. */
    public static FeatureInputDto minimal(String clientIdExterne, String imfCode) {
        return new FeatureInputDto(
            clientIdExterne, imfCode, null, null,
            null, null, null, null, null, null, null,
            null, null, null, null, null, null, null,
            null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null
        );
    }
}
