package cm.imf.pipeline.dto.response;

public record ClientResponse(
        String idClient,
        String nomClient,
        String telephoneClient,
        String agencePrincipale
) {}
