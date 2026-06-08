package cm.imf.pipeline.ml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * DTO Java miroir du schéma Pydantic ScoreResponse de l'API FastAPI MCRS.
 * Désérialisé depuis la réponse JSON de POST /score/single.
 */
public record ScoringResultDto(

    @JsonProperty("client_id_externe")       String clientIdExterne,
    @JsonProperty("imf_code")                String imfCode,
    @JsonProperty("score_crs")               double scoreCrs,
    @JsonProperty("score_rps")               double scoreRps,
    @JsonProperty("score_csi")               double scoreCsi,
    @JsonProperty("score_mcrs")              double scoreMcrs,
    @JsonProperty("classe_risque")           String classeRisque,
    @JsonProperty("probabilite_defaut_30j")  double probabiliteDefaut30j,
    @JsonProperty("probabilite_defaut_90j")  double probabiliteDefaut90j,
    @JsonProperty("score_mcrs_ic_bas")       double scoreMcrsIcBas,
    @JsonProperty("score_mcrs_ic_haut")      double scoreMcrsIcHaut,
    @JsonProperty("action_recommandee")      String actionRecommandee,
    @JsonProperty("priorite_recouvrement")   int prioriteRecouvrement,
    @JsonProperty("region_id")               String regionId,
    @JsonProperty("region_name")             String regionName,
    @JsonProperty("seuil_operationnel")      Double seuilOperationnel,
    @JsonProperty("revue_humaine_requise")   boolean revueHumaineRequise,
    @JsonProperty("decision_operationnelle") String decisionOperationnelle,
    @JsonProperty("shap_values")             Map<String, Double> shapValues,
    @JsonProperty("scored_at")               String scoredAt

) {}
