package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.*;
import cm.imf.pipeline.dto.response.*;
import cm.imf.pipeline.entity.User;

import java.util.List;
import java.util.UUID;

public interface ICreditService {

    // ── Dossiers crédit ───────────────────────────────────────────────────────

    DossierCreditResponse creerDossier(CreerDossierCreditRequest request, User currentUser);

    PageResponse<DossierCreditResponse> listDossiers(User currentUser, String statut, int page, int size);

    DossierCreditResponse getDossier(UUID uid);

    DossierCreditResponse soumettre(UUID uid, User currentUser);

    DossierCreditResponse validerChef(UUID uid, ValidationChefRequest request, User currentUser);

    DossierCreditResponse clotureInstruction(UUID uid, String noteAnalyse, User currentUser);

    // ── Garanties ─────────────────────────────────────────────────────────────

    GarantieCreditResponse ajouterGarantie(UUID dossierUid, AjouterGarantieRequest request, User currentUser);

    List<GarantieCreditResponse> listGaranties(UUID dossierUid);

    // ── Comité ────────────────────────────────────────────────────────────────

    ComiteDecisionResponse ouvrirSeance(UUID dossierUid, OuvrirSeanceComiteRequest request, User currentUser);

    VoteComiteResponse voter(UUID dossierUid, VoterComiteRequest request, User currentUser);

    DossierCreditResponse enregistrerDecision(UUID dossierUid, DecisionComiteRequest request, User currentUser);

    List<ComiteDecisionResponse> listComites(UUID dossierUid);

    List<VoteComiteResponse> listVotes(UUID comiteUid);

    // ── Visite de conformité J+15 ─────────────────────────────────────────────

    VisiteConformiteResponse enregistrerVisite(UUID dossierUid, VisiteConformiteRequest request, User currentUser);

    List<VisiteConformiteResponse> listVisites(UUID dossierUid);
}
