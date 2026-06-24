package cm.imf.pipeline.dto.response;

public record ClientResponse(
        String idClient,
        String nomClient,
        String telephoneClient,
        String agencePrincipale,
        Double encours,
        String statut
) {
    public ClientResponse(String idClient, String nomClient, String telephoneClient, String agencePrincipale) {
        this(idClient, nomClient, telephoneClient, agencePrincipale, 0.0, "ACTIF");
    }
}
