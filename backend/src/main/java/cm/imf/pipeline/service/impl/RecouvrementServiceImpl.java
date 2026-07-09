package cm.imf.pipeline.service.impl;

import cm.imf.pipeline.dto.request.AccordReechelonnementRequest;
import cm.imf.pipeline.dto.request.AjouterActionRequest;
import cm.imf.pipeline.dto.request.EscaladerDossierRequest;
import cm.imf.pipeline.dto.request.OuvrirDossierRequest;
import cm.imf.pipeline.dto.response.AccordReechelonnementResponse;
import cm.imf.pipeline.dto.response.ActionRecouvrementResponse;
import cm.imf.pipeline.dto.response.DossierRecouvrementResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.entity.AccordReechelonnement;
import cm.imf.pipeline.entity.ActionRecouvrement;
import cm.imf.pipeline.entity.Creance;
import cm.imf.pipeline.entity.RecouvrementDossier;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.RecouvrementPhase;
import cm.imf.pipeline.event.SyncCompletedEvent;
import cm.imf.pipeline.repository.AccordReechelonnementRepository;
import cm.imf.pipeline.repository.ActionRecouvrementRepository;
import cm.imf.pipeline.repository.CreanceRepository;
import cm.imf.pipeline.repository.RecouvrementDossierRepository;
import cm.imf.pipeline.repository.UserRepository;
import cm.imf.pipeline.service.IRecouvrementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecouvrementServiceImpl implements IRecouvrementService {

    private final RecouvrementDossierRepository dossierRepo;
    private final ActionRecouvrementRepository  actionRepo;
    private final AccordReechelonnementRepository accordRepo;
    private final UserRepository userRepo;
    private final CreanceRepository creanceRepo;
    private final ApplicationEventPublisher eventPublisher;

    // ── Ouvrir un dossier ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public DossierRecouvrementResponse ouvrirDossier(OuvrirDossierRequest req, User currentUser) {
        if (currentUser.getImf() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé : pas de tenant associé.");
        }
        Long imfId = currentUser.getImf().getId();

        if (dossierRepo.existsDossierActif(imfId, req.idPret())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un dossier de recouvrement actif existe déjà pour le prêt " + req.idPret());
        }

        User agent = resolveUser(req.agentResponsableUid());

        RecouvrementDossier dossier = RecouvrementDossier.builder()
                .imfId(imfId)
                .idPret(req.idPret())
                .nomClient(req.nomClient())
                .montantImpaye(req.montantImpaye())
                .joursRetard(req.joursRetard())
                .datePremiereEcheanceImpayee(req.datePremiereEcheanceImpayee())
                .nomCaution(req.nomCaution())
                .telephoneCaution(req.telephoneCaution())
                .typeGarantie(req.typeGarantie())
                .phase(RecouvrementPhase.RELANCE_AMIABLE)
                .agentResponsable(agent)
                .build();

        // recalculerCobtac() est appelé dans @PrePersist
        dossier = dossierRepo.save(dossier);
        log.info("Dossier recouvrement ouvert — prêt={} imf={} catégorieCobtac={}",
                req.idPret(), imfId, dossier.getCategorieCobtac());

        // Déclenche le scoring MCRS temps réel pour ce client, comme après une
        // synchronisation mobile — jusqu'ici l'ouverture d'un dossier n'en
        // déclenchait aucun, donc un client jamais synchronisé via l'app mobile
        // pouvait rester non scoré indéfiniment. Réutilise SyncCompletedEvent
        // (nom hérité du cas d'usage sync, mais le listener ne lit que
        // clientIds/imfId/agentUsername — syncResponse reste null ici, sans
        // effet) pour bénéficier du même traitement async après-commit.
        creanceRepo.findByImf_IdAndIdPretExterne(imfId, req.idPret()).ifPresentOrElse(
                creance -> eventPublisher.publishEvent(new SyncCompletedEvent(
                        this, null, currentUser.getUsername(),
                        List.of(creance.getClientIdExterne()), imfId)),
                () -> log.debug("Créance introuvable pour prêt={} imf={} — scoring temps réel non déclenché",
                        req.idPret(), imfId));

        return DossierRecouvrementResponse.from(dossier);
    }

    // ── Lister les dossiers ───────────────────────────────────────────────────

    @Override
    public PageResponse<DossierRecouvrementResponse> listDossiers(
            Long imfId, RecouvrementPhase phase, Boolean clos, int page, int size) {

        // Priorité MCRS d'abord (1 faible → 5 critique, écrite par le pipeline via
        // maj_priorites_dossiers_recouvrement) ; joursRetard en repli/départage
        // pour les dossiers pas encore scorés (prioriteScoring NULL) ou à égalité.
        Sort sort = Sort.by(
                Sort.Order.desc("prioriteScoring").nullsLast(),
                Sort.Order.desc("joursRetard"));
        PageRequest pageable = PageRequest.of(page, size, sort);
        Page<RecouvrementDossier> result;

        if (phase != null && clos != null) {
            result = dossierRepo.findByImfIdAndPhaseAndClos(imfId, phase, clos, pageable);
        } else if (phase != null) {
            result = dossierRepo.findByImfIdAndPhase(imfId, phase, pageable);
        } else if (clos != null) {
            result = dossierRepo.findByImfIdAndClos(imfId, clos, pageable);
        } else {
            result = dossierRepo.findByImfId(imfId, pageable);
        }

        return PageResponse.from(result, DossierRecouvrementResponse::from);
    }

    // ── Détail d'un dossier ───────────────────────────────────────────────────

    @Override
    public DossierRecouvrementResponse getDossier(UUID uid) {
        return DossierRecouvrementResponse.from(findDossierOrThrow(uid));
    }

    // ── Ajouter une action ────────────────────────────────────────────────────

    @Override
    @Transactional
    public ActionRecouvrementResponse ajouterAction(UUID dossierUid, AjouterActionRequest req, User currentUser) {
        RecouvrementDossier dossier = findDossierOrThrow(dossierUid);

        if (dossier.isClos()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Impossible d'ajouter une action à un dossier clôturé.");
        }

        ActionRecouvrement action = ActionRecouvrement.builder()
                .dossier(dossier)
                .typeAction(req.typeAction())
                .resultat(req.resultat())
                .promesseDate(req.promesseDate())
                .promesseMontant(req.promesseMontant())
                .canalPaiement(req.canalPaiement())
                .referenceTransaction(req.referenceTransaction())
                .numeroTelephonePaiement(req.numeroTelephonePaiement())
                .statutVerifMomo(req.statutVerifMomo())
                .fraisEngages(req.fraisEngages())
                .observation(req.observation())
                .agent(currentUser)
                .build();

        action = actionRepo.save(action);

        // Cumuler les frais engagés sur le dossier
        if (req.fraisEngages() != null && req.fraisEngages().compareTo(BigDecimal.ZERO) > 0) {
            dossier.setFraisRecouvrement(dossier.getFraisRecouvrement().add(req.fraisEngages()));
        }
        dossier.setDateDerniereAction(OffsetDateTime.now());
        dossierRepo.save(dossier);

        log.info("Action {} ajoutée au dossier {} par {}", req.typeAction(), dossierUid, currentUser.getUsername());
        return ActionRecouvrementResponse.from(action);
    }

    // ── Escalader / changer la phase ──────────────────────────────────────────

    @Override
    @Transactional
    public DossierRecouvrementResponse escalader(UUID dossierUid, EscaladerDossierRequest req, User currentUser) {
        RecouvrementDossier dossier = findDossierOrThrow(dossierUid);

        if (dossier.isClos()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Impossible d'escalader un dossier clôturé.");
        }
        if (dossier.getPhase() == req.nouvellePhase()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Le dossier est déjà en phase " + req.nouvellePhase());
        }

        RecouvrementPhase anciennePhase = dossier.getPhase();
        dossier.setPhase(req.nouvellePhase());
        dossier.setDateDerniereAction(OffsetDateTime.now());
        dossier = dossierRepo.save(dossier);

        log.info("Dossier {} : {} → {} par {}", dossierUid, anciennePhase, req.nouvellePhase(), currentUser.getUsername());
        return DossierRecouvrementResponse.from(dossier);
    }

    // ── Clôturer un dossier ───────────────────────────────────────────────────

    @Override
    @Transactional
    public DossierRecouvrementResponse clore(UUID dossierUid, String motif, User currentUser) {
        RecouvrementDossier dossier = findDossierOrThrow(dossierUid);

        if (dossier.isClos()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Ce dossier est déjà clôturé.");
        }

        dossier.setClos(true);
        dossier.setDateCloture(OffsetDateTime.now());
        dossier.setMotifCloture(motif);
        dossier = dossierRepo.save(dossier);

        log.info("Dossier {} clôturé par {} — motif: {}", dossierUid, currentUser.getUsername(), motif);
        return DossierRecouvrementResponse.from(dossier);
    }

    // ── Actions d'un dossier ──────────────────────────────────────────────────

    @Override
    public List<ActionRecouvrementResponse> getActions(UUID dossierUid) {
        RecouvrementDossier dossier = findDossierOrThrow(dossierUid);
        return actionRepo.findByDossierIdOrderByDateActionDesc(dossier.getId())
                .stream()
                .map(ActionRecouvrementResponse::from)
                .toList();
    }

    // ── Accord de rééchelonnement ─────────────────────────────────────────────

    @Override
    @Transactional
    public AccordReechelonnementResponse creerAccord(UUID dossierUid, AccordReechelonnementRequest req, User currentUser) {
        RecouvrementDossier dossier = findDossierOrThrow(dossierUid);

        if (dossier.isClos()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Impossible de créer un accord sur un dossier clôturé.");
        }

        // Désactiver l'accord actif précédent s'il existe
        accordRepo.findByDossierIdAndActifTrue(dossier.getId()).ifPresent(ancien -> {
            ancien.setActif(false);
            accordRepo.save(ancien);
        });

        User approuvePar = resolveUser(req.approuveParUid());

        AccordReechelonnement accord = AccordReechelonnement.builder()
                .dossier(dossier)
                .nouveauMontantMensuel(req.nouveauMontantMensuel())
                .nombreNouvellesEcheances(req.nombreNouvellesEcheances())
                .dateDebutNouvelEcheancier(req.dateDebutNouvelEcheancier())
                .tauxInteretAnnuel(req.tauxInteretAnnuel())
                .approuvePar(approuvePar)
                .dateSignature(req.dateSignature())
                .observations(req.observations())
                .build();

        accord = accordRepo.save(accord);

        // Basculer le dossier en phase REECHELONNEMENT
        dossier.setPhase(RecouvrementPhase.REECHELONNEMENT);
        dossier.setDateDerniereAction(OffsetDateTime.now());
        dossierRepo.save(dossier);

        log.info("Accord de rééchelonnement créé — dossier={} par {}", dossierUid, currentUser.getUsername());
        return AccordReechelonnementResponse.from(accord);
    }

    @Override
    public List<AccordReechelonnementResponse> getAccords(UUID dossierUid) {
        RecouvrementDossier dossier = findDossierOrThrow(dossierUid);
        return accordRepo.findByDossierIdOrderByCreatedAtDesc(dossier.getId())
                .stream()
                .map(AccordReechelonnementResponse::from)
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RecouvrementDossier findDossierOrThrow(UUID uid) {
        return dossierRepo.findByUid(uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dossier de recouvrement introuvable : " + uid));
    }

    private User resolveUser(java.util.UUID userUid) {
        if (userUid == null) return null;
        return userRepo.findByUid(userUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable : " + userUid));
    }
}
