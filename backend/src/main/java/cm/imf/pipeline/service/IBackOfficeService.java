package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.EncaissementRequest;
import cm.imf.pipeline.dto.request.ExecuterDecaissementRequest;
import cm.imf.pipeline.dto.request.GenererContratRequest;
import cm.imf.pipeline.dto.response.ContratCreditResponse;
import cm.imf.pipeline.dto.response.DecaissementResponse;
import cm.imf.pipeline.dto.response.OperationCaisseResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.entity.User;

import java.util.UUID;

public interface IBackOfficeService {

    // ── Contrats ──────────────────────────────────────────────────────────────

    ContratCreditResponse genererContrat(UUID dossierUid, GenererContratRequest request, User currentUser);

    ContratCreditResponse validerSignatures(UUID contratUid, User currentUser);

    ContratCreditResponse getContrat(UUID contratUid);

    ContratCreditResponse getContratParDossier(UUID dossierUid);

    // ── Décaissements ─────────────────────────────────────────────────────────

    DecaissementResponse executerDecaissement(ExecuterDecaissementRequest request, User currentUser);

    // ── Journal de caisse ─────────────────────────────────────────────────────

    OperationCaisseResponse enregistrerEncaissement(EncaissementRequest request, User currentUser);

    PageResponse<OperationCaisseResponse> journalCaisse(User currentUser, int page, int size);
}
