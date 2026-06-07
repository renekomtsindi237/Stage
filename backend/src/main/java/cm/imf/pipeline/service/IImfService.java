package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.CreateImfAdminRequest;
import cm.imf.pipeline.dto.request.CreateImfRequest;
import cm.imf.pipeline.dto.response.ImfResponse;
import cm.imf.pipeline.dto.response.PlatformStatsResponse;

import java.util.List;
import java.util.UUID;

public interface IImfService {

    PlatformStatsResponse getStats();

    List<ImfResponse> listAll();

    ImfResponse getById(UUID uid);

    ImfResponse create(CreateImfRequest request);

    ImfResponse deactivate(UUID uid);

    ImfResponse activate(UUID uid);

    /** Supprime définitivement une IMF (et tous ses utilisateurs). Irréversible. */
    void delete(UUID uid);

    /** Crée le compte DSI initial d'une IMF — retourne l'IMF mise à jour (hasDsi = true). */
    ImfResponse createAdmin(UUID imfUid, CreateImfAdminRequest request);
}
