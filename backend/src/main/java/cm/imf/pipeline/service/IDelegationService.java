package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.DeleguerAutoriteRequest;
import cm.imf.pipeline.dto.request.ReassignerDossierRequest;
import cm.imf.pipeline.dto.response.DelegationResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.dto.response.UserResponse;
import cm.imf.pipeline.entity.User;

import java.util.List;
import java.util.UUID;

public interface IDelegationService {

    /** Transfère un dossier crédit d'un agent à un autre. Crée un enregistrement d'audit. */
    DelegationResponse reassignerDossier(UUID dossierUid, ReassignerDossierRequest req, User delegant);

    /** Crée une délégation d'autorité temporaire (validation, signature comité...). */
    DelegationResponse deleguerAutorite(DeleguerAutoriteRequest req, User delegant);

    /** Révoque une délégation d'autorité active. Seul le délégant ou le DIRECTEUR peut révoquer. */
    void revoquerDelegation(UUID delegationUid, User demandeur);

    /** Liste paginée de toutes les délégations de l'IMF (DIRECTEUR, DSI). */
    PageResponse<DelegationResponse> listDelegationsImf(User user, int page, int size);

    /** Délégations d'autorité actives reçues par l'utilisateur connecté. */
    List<DelegationResponse> mesDelegations(User user);

    /** Liste des AGENT_CREDIT actifs de l'IMF — pour alimenter le select de réassignation. */
    List<UserResponse> getAgentsCredit(User user);
}
