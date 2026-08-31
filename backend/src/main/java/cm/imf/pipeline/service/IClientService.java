package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.response.ClientDossierResponse;
import cm.imf.pipeline.dto.response.ClientResponse;

import java.util.List;

public interface IClientService {

    List<ClientResponse> search(String query, int limit);

    ClientResponse getById(String idClient);

    ClientDossierResponse getDossier(String idClient);

    List<ClientResponse> list(int page, int size, String search, String statut, String agence);

    long count(String search, String statut, String agence);
}
