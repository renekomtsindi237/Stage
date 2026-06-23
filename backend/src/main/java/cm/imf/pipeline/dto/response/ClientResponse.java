package cm.imf.pipeline.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClientResponse(
        @JsonProperty("id") String idClient,
        @JsonProperty("nom") String nomClient,
        @JsonProperty("telephone") String telephoneClient,
        @JsonProperty("agenceNom") String agencePrincipale
) {}
