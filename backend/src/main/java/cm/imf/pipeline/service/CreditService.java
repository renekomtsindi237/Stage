package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.*;
import cm.imf.pipeline.dto.response.*;
import cm.imf.pipeline.entity.*;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditService implements ICreditService {

    private final DossierCreditRepository dossierRepo;
    private final GarantieCreditRepository garantieRepo;
    private final ComiteDecisionRepository comiteRepo;
    private final VoteComiteRepository voteRepo;
    private final VisiteConformiteRepository visiteRepo;

    private static final Set<String> STATUTS_VALIDES = Set.of(
            "INSTRUCTION", "EN_COMITE", "APPROUVE", "REJETE", "AJOURNE", "DEBLOQUE");

    // ── Dossiers ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public DossierCreditResponse creerDossier(CreerDossierCreditRequest req, User user) {
        requireTenant(user);
        DossierCredit dossier = DossierCredit.builder()
                .imfId(user.getImf().getId())
                .agenceId(req.agenceId())
                .agentCreditId(user.getId())
                .clientId(req.clientId())
                .clientNom(req.clientNom())
                .montantDemande(req.montantDemande())
                .dureeMois(req.dureeMois())
                .objetFinancement(req.objetFinancement())
                .secteurActivite(req.secteurActivite())
                .revenuEstime(req.revenuEstime())
                .chargesMensuelles(req.chargesMensuelles())
                .build();
        return DossierCreditResponse.from(dossierRepo.save(dossier));
    }

    @Override
    public PageResponse<DossierCreditResponse> listDossiers(User user, String statut, int page, int size) {
        requireTenant(user);
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        var role = user.getRole();

        // AGENT_CREDIT voit uniquement ses propres dossiers
        if (role == Role.AGENT_CREDIT) {
            var p = (statut != null)
                    ? dossierRepo.findByAgentCreditIdAndStatut(user.getId(), statut, pageable)
                    : dossierRepo.findByAgentCreditId(user.getId(), pageable);
            return PageResponse.from(p, DossierCreditResponse::from);
        }

        // CHEF_AGENCE, DIRECTEUR, ANALYSTE_ENGAGEMENTS, DSI, SUPER_ADMIN voient toute l'IMF
        var p = (statut != null)
                ? dossierRepo.findByImfIdAndStatut(user.getImf().getId(), statut, pageable)
                : dossierRepo.findByImfId(user.getImf().getId(), pageable);
        return PageResponse.from(p, DossierCreditResponse::from);
    }

    @Override
    public DossierCreditResponse getDossier(UUID uid) {
        return DossierCreditResponse.from(findDossierByUid(uid));
    }

    @Override
    @Transactional
    public DossierCreditResponse soumettre(UUID uid, User user) {
        DossierCredit dossier = findDossierByUid(uid);
        requireOwnerOrManager(dossier, user);
        if (!"INSTRUCTION".equals(dossier.getStatut())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seul un dossier en INSTRUCTION peut être soumis.");
        }
        dossier.setStatut("EN_COMITE");
        dossier.setDateSoumission(java.time.OffsetDateTime.now());
        return DossierCreditResponse.from(dossierRepo.save(dossier));
    }

    @Override
    @Transactional
    public DossierCreditResponse validerChef(UUID uid, ValidationChefRequest req, User user) {
        DossierCredit dossier = findDossierByUid(uid);
        requireTenant(user);
        if (!"EN_COMITE".equals(dossier.getStatut()) && !"INSTRUCTION".equals(dossier.getStatut())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le dossier n'est pas dans un état validable par le Chef d'Agence.");
        }
        String action = req.action().toUpperCase();
        if ("VALIDER".equals(action)) {
            dossier.setStatut("EN_COMITE");
            dossier.setChefAgenceId(user.getId());
        } else if ("REJETER".equals(action)) {
            dossier.setStatut("REJETE");
            dossier.setNoteAnalyse(req.motif());
            dossier.setDateDecision(java.time.OffsetDateTime.now());
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Action invalide. Valeurs acceptées : VALIDER, REJETER.");
        }
        return DossierCreditResponse.from(dossierRepo.save(dossier));
    }

    @Override
    @Transactional
    public DossierCreditResponse clotureInstruction(UUID uid, String noteAnalyse, User user) {
        DossierCredit dossier = findDossierByUid(uid);
        requireTenant(user);
        dossier.setNoteAnalyse(noteAnalyse);
        return DossierCreditResponse.from(dossierRepo.save(dossier));
    }

    // ── Garanties ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public GarantieCreditResponse ajouterGarantie(UUID dossierUid, AjouterGarantieRequest req, User user) {
        DossierCredit dossier = findDossierByUid(dossierUid);
        requireTenant(user);
        GarantieCredit garantie = GarantieCredit.builder()
                .dossierId(dossier.getId())
                .type(req.type())
                .description(req.description())
                .valeurEstimee(req.valeurEstimee())
                .referenceDocument(req.referenceDocument())
                .cautionNom(req.cautionNom())
                .cautionTelephone(req.cautionTelephone())
                .build();
        return GarantieCreditResponse.from(garantieRepo.save(garantie));
    }

    @Override
    public List<GarantieCreditResponse> listGaranties(UUID dossierUid) {
        DossierCredit dossier = findDossierByUid(dossierUid);
        return garantieRepo.findByDossierId(dossier.getId())
                .stream().map(GarantieCreditResponse::from).toList();
    }

    // ── Comité ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ComiteDecisionResponse ouvrirSeance(UUID dossierUid, OuvrirSeanceComiteRequest req, User user) {
        DossierCredit dossier = findDossierByUid(dossierUid);
        requireTenant(user);
        ComiteDecision comite = ComiteDecision.builder()
                .dossierId(dossier.getId())
                .typeComite(req.typeComite())
                .presidentId(user.getId())
                .dateSeance(req.dateSeance())
                .build();
        return ComiteDecisionResponse.from(comiteRepo.save(comite));
    }

    @Override
    @Transactional
    public VoteComiteResponse voter(UUID dossierUid, VoterComiteRequest req, User user) {
        ComiteDecision comite = comiteRepo.findByUid(req.comiteUid())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance comité introuvable."));
        if (voteRepo.findByComiteIdAndVotantId(comite.getId(), user.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vous avez déjà voté pour cette séance.");
        }
        VoteComite vote = VoteComite.builder()
                .comiteId(comite.getId())
                .votantId(user.getId())
                .roleVotant(user.getRole().name())
                .vote(req.vote().toUpperCase())
                .commentaire(req.commentaire())
                .build();
        return VoteComiteResponse.from(voteRepo.save(vote));
    }

    @Override
    @Transactional
    public DossierCreditResponse enregistrerDecision(UUID dossierUid, DecisionComiteRequest req, User user) {
        DossierCredit dossier = findDossierByUid(dossierUid);
        requireTenant(user);
        ComiteDecision comite = comiteRepo.findByUid(req.comiteUid())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance comité introuvable."));
        comite.setDecision(req.decision().toUpperCase());
        comite.setMontantApprouve(req.montantApprouve());
        comite.setTauxApprouve(req.tauxApprouve());
        comite.setDureeApprouvee(req.dureeApprouvee());
        comite.setConditions(req.conditions());
        comite.setMotifRejet(req.motifRejet());
        comite.setQuorumAtteint(voteRepo.countByComiteId(comite.getId()) >= 2);
        comiteRepo.save(comite);

        String nouveauStatut = switch (req.decision().toUpperCase()) {
            case "APPROUVE"    -> "APPROUVE";
            case "REJETE"      -> "REJETE";
            case "AJOURNE"     -> "AJOURNE";
            case "RESTRUCTURE" -> "AJOURNE";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Décision invalide. Valeurs : APPROUVE, REJETE, AJOURNE, RESTRUCTURE.");
        };
        dossier.setStatut(nouveauStatut);
        dossier.setDateDecision(java.time.OffsetDateTime.now());
        return DossierCreditResponse.from(dossierRepo.save(dossier));
    }

    @Override
    public List<ComiteDecisionResponse> listComites(UUID dossierUid) {
        DossierCredit dossier = findDossierByUid(dossierUid);
        return comiteRepo.findByDossierIdOrderByCreatedAtDesc(dossier.getId())
                .stream().map(ComiteDecisionResponse::from).toList();
    }

    @Override
    public List<VoteComiteResponse> listVotes(UUID comiteUid) {
        ComiteDecision comite = comiteRepo.findByUid(comiteUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance comité introuvable."));
        return voteRepo.findByComiteId(comite.getId())
                .stream().map(VoteComiteResponse::from).toList();
    }

    // ── Visites ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public VisiteConformiteResponse enregistrerVisite(UUID dossierUid, VisiteConformiteRequest req, User user) {
        DossierCredit dossier = findDossierByUid(dossierUid);
        requireTenant(user);
        VisiteConformite visite = VisiteConformite.builder()
                .dossierId(dossier.getId())
                .agentCreditId(user.getId())
                .dateVisite(req.dateVisite())
                .conformiteObservee(req.conformiteObservee())
                .observations(req.observations())
                .latitude(req.latitude())
                .longitude(req.longitude())
                .build();
        return VisiteConformiteResponse.from(visiteRepo.save(visite));
    }

    @Override
    public List<VisiteConformiteResponse> listVisites(UUID dossierUid) {
        DossierCredit dossier = findDossierByUid(dossierUid);
        return visiteRepo.findByDossierIdOrderByCreatedAtDesc(dossier.getId())
                .stream().map(VisiteConformiteResponse::from).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DossierCredit findDossierByUid(UUID uid) {
        return dossierRepo.findByUid(uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier crédit introuvable."));
    }

    private void requireTenant(User user) {
        if (user.getImf() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Le SUPER_ADMIN n'a pas accès aux dossiers de crédit.");
        }
    }

    private void requireOwnerOrManager(DossierCredit dossier, User user) {
        requireTenant(user);
        var role = user.getRole();
        boolean isOwner = dossier.getAgentCreditId().equals(user.getId());
        boolean isManager = role == Role.CHEF_AGENCE || role == Role.DIRECTEUR
                || role == Role.DSI || role == Role.ANALYSTE_ENGAGEMENTS;
        if (!isOwner && !isManager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous n'êtes pas autorisé à modifier ce dossier.");
        }
    }
}
