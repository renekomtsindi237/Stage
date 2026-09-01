package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.AccordReechelonnementRequest;
import cm.imf.pipeline.dto.request.AjouterActionRequest;
import cm.imf.pipeline.dto.request.EscaladerDossierRequest;
import cm.imf.pipeline.dto.request.OuvrirDossierRequest;
import cm.imf.pipeline.dto.response.AccordReechelonnementResponse;
import cm.imf.pipeline.dto.response.ActionRecouvrementResponse;
import cm.imf.pipeline.dto.response.DossierRecouvrementResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.RecouvrementPhase;

import java.util.List;
import java.util.UUID;

/**
 * Workflow de recouvrement des créances — Cameroun/OHADA.
 *
 * Phases : RELANCE_AMIABLE → MEDIATION_AMIABLE → MISE_EN_DEMEURE → CONTENTIEUX
 *           REECHELONNEMENT / PERTE
 */
public interface IRecouvrementService {

    /** Ouvre un dossier de recouvrement pour un prêt en retard. */
    DossierRecouvrementResponse ouvrirDossier(OuvrirDossierRequest request, User currentUser);

    /** Liste paginée des dossiers de l'IMF, avec filtres optionnels (phase, clos, recherche). */
    PageResponse<DossierRecouvrementResponse> listDossiers(
            Long imfId, RecouvrementPhase phase, Boolean clos, int page, int size, String q);

    /** Détail d'un dossier par son UID public. */
    DossierRecouvrementResponse getDossier(UUID uid);

    /** Enregistre une action de recouvrement dans le dossier et cumule les frais éventuels. */
    ActionRecouvrementResponse ajouterAction(UUID dossierUid, AjouterActionRequest request, User currentUser);

    /** Escalade ou change la phase du dossier. */
    DossierRecouvrementResponse escalader(UUID dossierUid, EscaladerDossierRequest request, User currentUser);

    /** Clôture le dossier (paiement total, radiation ou accord). */
    DossierRecouvrementResponse clore(UUID dossierUid, String motif, User currentUser);

    /** Liste chronologique des actions d'un dossier (plus récent en premier). */
    List<ActionRecouvrementResponse> getActions(UUID dossierUid);

    /** Crée un accord de rééchelonnement formel pour un dossier. */
    AccordReechelonnementResponse creerAccord(UUID dossierUid, AccordReechelonnementRequest request, User currentUser);

    /** Liste des accords de rééchelonnement d'un dossier. */
    List<AccordReechelonnementResponse> getAccords(UUID dossierUid);
}
