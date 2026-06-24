package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.*;
import cm.imf.pipeline.dto.response.*;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.NiveauKyc;
import cm.imf.pipeline.enums.NiveauRisque;
import cm.imf.pipeline.enums.StatutKyc;

import java.util.List;
import java.util.UUID;

public interface IKycService {

    // ── Dossiers ─────────────────────────────────────────────────────────────
    KycDossierResponse initierDossier(InitierKycRequest req, User currentUser);
    PageResponse<KycDossierResponse> listDossiers(Long imfId, StatutKyc statut, NiveauKyc niveau, NiveauRisque risque, int page, int size);
    KycDossierResponse getDossier(UUID uid);
    KycDossierResponse evaluerRisque(UUID uid, EvaluerRisqueKycRequest req, User currentUser);

    // ── Documents ────────────────────────────────────────────────────────────
    KycDocumentResponse soumettreDocument(UUID dossierUid, SoumettreDocumentKycRequest req, User currentUser);
    List<KycDocumentResponse> getDocuments(UUID dossierUid);
    KycDocumentResponse validerDocument(UUID documentUid, ValiderDocumentKycRequest req, User currentUser);

    record DocumentContenu(byte[] data, String mimeType, String nomFichier) {}
    DocumentContenu telechargerContenu(UUID documentUid);

    // ── Vérification ─────────────────────────────────────────────────────────
    KycDossierResponse verifier(UUID dossierUid, VerifierKycRequest req, User currentUser);
    List<KycVerificationResponse> getHistoriqueVerifications(UUID dossierUid);
}
