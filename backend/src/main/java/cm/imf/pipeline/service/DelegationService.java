package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.DeleguerAutoriteRequest;
import cm.imf.pipeline.dto.request.ReassignerDossierRequest;
import cm.imf.pipeline.dto.response.DelegationResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.dto.response.UserResponse;
import cm.imf.pipeline.entity.Delegation;
import cm.imf.pipeline.entity.DossierCredit;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.repository.DelegationRepository;
import cm.imf.pipeline.repository.DossierCreditRepository;
import cm.imf.pipeline.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DelegationService implements IDelegationService {

    private final DelegationRepository delegationRepo;
    private final DossierCreditRepository dossierRepo;
    private final UserRepository userRepo;

    /**
     * Hiérarchie de délégation IMF :
     *   DIRECTEUR → tous les rôles opérationnels
     *   CHEF_AGENCE → ANALYSTE_ENGAGEMENTS, AGENT_CREDIT
     *   RESPONSABLE_RECOUVREMENT → AGENT
     *
     * DSI et SUPER_ADMIN sont des rôles système — ils n'ont pas d'autorité
     * opérationnelle à déléguer dans le workflow crédit/recouvrement.
     */
    private static final Map<Role, EnumSet<Role>> PEUT_DELEGUER_A = Map.of(
            Role.DIRECTEUR, EnumSet.of(
                    Role.CHEF_AGENCE, Role.ANALYSTE_ENGAGEMENTS, Role.AGENT_CREDIT,
                    Role.AGENT_SAISIE, Role.CAISSIER, Role.RESPONSABLE_RECOUVREMENT, Role.AGENT),
            Role.CHEF_AGENCE, EnumSet.of(
                    Role.ANALYSTE_ENGAGEMENTS, Role.AGENT_CREDIT),
            Role.RESPONSABLE_RECOUVREMENT, EnumSet.of(
                    Role.AGENT)
    );

    // ── Réassignation de dossier ───────────────────────────────────────────────

    @Override
    @Transactional
    public DelegationResponse reassignerDossier(UUID dossierUid, ReassignerDossierRequest req, User delegant) {
        requireTenant(delegant);

        DossierCredit dossier = dossierRepo.findByUid(dossierUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dossier crédit introuvable."));

        requireSameImf(delegant, dossier.getImfId());
        requirePeutDeleguer(delegant.getRole());

        User nouvelAgent = userRepo.findByUid(req.nouvelAgentUid())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Utilisateur cible introuvable."));

        requireSameImf(delegant, nouvelAgent.getImf() == null ? null : nouvelAgent.getImf().getId());

        if (nouvelAgent.getRole() != Role.AGENT_CREDIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un dossier crédit ne peut être réassigné qu'à un AGENT_CREDIT.");
        }

        if (nouvelAgent.getId().equals(dossier.getAgentCreditId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce dossier est déjà assigné à cet agent.");
        }

        Long ancienAgentId = dossier.getAgentCreditId();
        dossier.setAgentCreditId(nouvelAgent.getId());
        dossierRepo.save(dossier);

        Delegation delegation = Delegation.builder()
                .imfId(delegant.getImf().getId())
                .delegantId(delegant.getId())
                .delegataireId(nouvelAgent.getId())
                .typeDelegation("REASSIGNATION_DOSSIER")
                .objetId(dossier.getId())
                .objetType("DOSSIER_CREDIT")
                .motif(req.motif())
                .build();

        return DelegationResponse.from(delegationRepo.save(delegation));
    }

    // ── Délégation d'autorité ─────────────────────────────────────────────────

    @Override
    @Transactional
    public DelegationResponse deleguerAutorite(DeleguerAutoriteRequest req, User delegant) {
        requireTenant(delegant);
        requirePeutDeleguer(delegant.getRole());

        User delegataire = userRepo.findByUid(req.delegataireUid())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Utilisateur délégataire introuvable."));

        requireSameImf(delegant, delegataire.getImf() == null ? null : delegataire.getImf().getId());

        EnumSet<Role> rolesAutorisés = PEUT_DELEGUER_A.get(delegant.getRole());
        if (!rolesAutorisés.contains(delegataire.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Un " + delegant.getRole() + " ne peut pas déléguer à un " + delegataire.getRole()
                            + ". Rôles autorisés : " + rolesAutorisés);
        }

        Delegation delegation = Delegation.builder()
                .imfId(delegant.getImf().getId())
                .delegantId(delegant.getId())
                .delegataireId(delegataire.getId())
                .typeDelegation("DELEGATION_AUTORITE")
                .roleDelegue(req.roleDelegue())
                .montantSeuil(req.montantSeuil())
                .dateFin(req.dateFin())
                .motif(req.motif())
                .build();

        return DelegationResponse.from(delegationRepo.save(delegation));
    }

    // ── Révocation ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void revoquerDelegation(UUID delegationUid, User demandeur) {
        requireTenant(demandeur);

        Delegation delegation = delegationRepo.findByUid(delegationUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Délégation introuvable."));

        requireSameImf(demandeur, delegation.getImfId());

        boolean estDelegant = delegation.getDelegantId().equals(demandeur.getId());
        boolean estDirecteur = demandeur.getRole() == Role.DIRECTEUR;
        boolean estDsi = demandeur.getRole() == Role.DSI;

        if (!estDelegant && !estDirecteur && !estDsi) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seul le délégant, le DIRECTEUR ou le DSI peut révoquer cette délégation.");
        }

        if (!delegation.isActif()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette délégation est déjà inactive.");
        }

        delegation.setActif(false);
        delegationRepo.save(delegation);
    }

    // ── Lectures ──────────────────────────────────────────────────────────────

    @Override
    public PageResponse<DelegationResponse> listDelegationsImf(User user, int page, int size) {
        requireTenant(user);
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return PageResponse.from(
                delegationRepo.findByImfId(user.getImf().getId(), pageable),
                DelegationResponse::from);
    }

    @Override
    public List<DelegationResponse> mesDelegations(User user) {
        return delegationRepo.findByDelegataireIdAndActif(user.getId(), true)
                .stream().map(DelegationResponse::from).toList();
    }

    @Override
    public List<UserResponse> getAgentsCredit(User user) {
        requireTenant(user);
        return userRepo.findByImfIdAndRoleIn(
                        user.getImf().getId(),
                        List.of(Role.AGENT_CREDIT))
                .stream()
                .filter(User::isActif)
                .map(UserResponse::from)
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void requireTenant(User user) {
        if (user.getImf() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Le SUPER_ADMIN n'a pas accès aux délégations IMF.");
        }
    }

    private void requireSameImf(User user, Long cibleImfId) {
        if (cibleImfId == null || !user.getImf().getId().equals(cibleImfId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La délégation doit rester au sein de la même IMF.");
        }
    }

    private void requirePeutDeleguer(Role role) {
        if (!PEUT_DELEGUER_A.containsKey(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Le rôle " + role + " n'a pas d'autorité à déléguer.");
        }
    }
}
