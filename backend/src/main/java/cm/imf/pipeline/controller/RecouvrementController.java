package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.AccordReechelonnementRequest;
import cm.imf.pipeline.dto.request.AjouterActionRequest;
import cm.imf.pipeline.dto.request.AlerteUpdateRequest;
import cm.imf.pipeline.dto.request.EscaladerDossierRequest;
import cm.imf.pipeline.dto.request.OuvrirDossierRequest;
import cm.imf.pipeline.dto.response.AccordReechelonnementResponse;
import cm.imf.pipeline.dto.response.ActionRecouvrementResponse;
import cm.imf.pipeline.dto.response.AlerteResponse;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.DossierRecouvrementResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.dto.response.RecDashboardResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.RecouvrementPhase;
import cm.imf.pipeline.enums.StatutAlerte;
import cm.imf.pipeline.repository.ActionRecouvrementRepository;
import cm.imf.pipeline.repository.RecouvrementDossierRepository;
import cm.imf.pipeline.service.IAlertService;
import cm.imf.pipeline.service.IRecouvrementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Workflow de recouvrement des créances — Cameroun/OHADA/COBAC.
 *
 * Phases : RELANCE_AMIABLE → MEDIATION_AMIABLE (chef quartier/famille)
 *       → MISE_EN_DEMEURE (huissier, OHADA art. 110) → CONTENTIEUX
 *       → REECHELONNEMENT | PERTE
 *
 * Canaux : MTN Mobile Money, Orange Money, Espèces, Virement.
 * Provisionnement COBAC automatique : EN_SURVEILLANCE 5% / DOUTEUSE 25% /
 *                                     LITIGIEUSE 50% / CONTENTIEUSE 100%.
 */
@RestController
@RequestMapping("/recouvrement")
@RequiredArgsConstructor
@Tag(name = "Recouvrement", description = "Workflow créances OHADA/COBAC — Cameroun")
public class RecouvrementController {

    private final IRecouvrementService recouvrementService;
    private final IAlertService alerteService;
    private final RecouvrementDossierRepository dossierRepo;
    private final ActionRecouvrementRepository actionRepo;

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @Operation(summary = "Tableau de bord du responsable recouvrement")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<RecDashboardResponse>> dashboard(
            @AuthenticationPrincipal User user) {
        requireTenant(user);
        Long imfId = user.getImf().getId();

        long actives = dossierRepo.countByImfIdAndClos(imfId, false);
        long total   = dossierRepo.countByImfId(imfId);
        BigDecimal montantRetard = dossierRepo.sumMontantImapyeActif(imfId);

        OffsetDateTime debut = OffsetDateTime.now().with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime fin   = debut.with(TemporalAdjusters.firstDayOfNextMonth());
        long actionsMois = actionRepo.countByImfIdAndPeriode(imfId, debut, fin);

        double taux = total == 0 ? 0.0
                : Math.round((double) (total - actives) / total * 10000.0) / 100.0;

        List<RecDashboardResponse.CreanceItem> creances = dossierRepo
                .findActivesOrderByJoursRetardDesc(imfId)
                .stream()
                .limit(5)
                .map(d -> new RecDashboardResponse.CreanceItem(
                        d.getUid() != null ? d.getUid().toString() : String.valueOf(d.getId()),
                        d.getNomClient() != null ? d.getNomClient() : d.getIdPret(),
                        d.getMontantImpaye(),
                        d.getJoursRetard(),
                        d.getCategorieCobtac() != null ? d.getCategorieCobtac().name() : "—",
                        d.getPhase() != null ? d.getPhase().name() : "—"))
                .toList();

        Map<String, Long> parPhase = new LinkedHashMap<>();
        for (RecouvrementPhase p : RecouvrementPhase.values()) {
            long cnt = dossierRepo.countByImfIdAndPhaseAndClos(imfId, p, false);
            if (cnt > 0) parPhase.put(p.name(), cnt);
        }

        return ResponseEntity.ok(ApiResponse.ok(new RecDashboardResponse(
                actives, montantRetard, actionsMois, taux, creances, parPhase)));
    }

    // ── Alertes (scoped à l'IMF du RESPONSABLE_RECOUVREMENT) ─────────────────

    @Operation(summary = "Liste paginée des alertes impayés de l'IMF")
    @GetMapping("/alertes")
    public ResponseEntity<ApiResponse<PageResponse<AlerteResponse>>> getAlertes(
            @RequestParam(required = false) StatutAlerte statut,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(alerteService.getAlertes(statut, page, size)));
    }

    @Operation(summary = "Prendre en charge une alerte (EN_TRAITEMENT)")
    @PatchMapping("/alertes/{uid}/traiter")
    public ResponseEntity<ApiResponse<AlerteResponse>> traiterAlerte(@PathVariable UUID uid) {
        return ResponseEntity.ok(ApiResponse.ok(
                alerteService.updateStatut(uid, new AlerteUpdateRequest(StatutAlerte.EN_TRAITEMENT))));
    }

    @Operation(summary = "Résoudre une alerte (RESOLUE / CLOTUREE)")
    @PatchMapping("/alertes/{uid}/resoudre")
    public ResponseEntity<ApiResponse<AlerteResponse>> resoudreAlerte(@PathVariable UUID uid) {
        return ResponseEntity.ok(ApiResponse.ok(
                alerteService.updateStatut(uid, new AlerteUpdateRequest(StatutAlerte.RESOLUE))));
    }

    // ── Dossiers ──────────────────────────────────────────────────────────────

    @Operation(summary = "Ouvrir un dossier de recouvrement (catégorie COBAC calculée automatiquement)")
    @PostMapping("/dossiers")
    public ResponseEntity<ApiResponse<DossierRecouvrementResponse>> ouvrirDossier(
            @Valid @RequestBody OuvrirDossierRequest request,
            @AuthenticationPrincipal User user) {
        requireTenant(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Dossier ouvert.", recouvrementService.ouvrirDossier(request, user)));
    }

    @Operation(summary = "Liste paginée des dossiers (filtres : phase, clos)")
    @GetMapping("/dossiers")
    public ResponseEntity<ApiResponse<PageResponse<DossierRecouvrementResponse>>> listDossiers(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) RecouvrementPhase phase,
            @RequestParam(required = false) Boolean clos,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        requireTenant(user);
        return ResponseEntity.ok(ApiResponse.ok(
                recouvrementService.listDossiers(user.getImf().getId(), phase, clos, page, size)));
    }

    @Operation(summary = "Détail d'un dossier")
    @GetMapping("/dossiers/{uid}")
    public ResponseEntity<ApiResponse<DossierRecouvrementResponse>> getDossier(@PathVariable UUID uid) {
        return ResponseEntity.ok(ApiResponse.ok(recouvrementService.getDossier(uid)));
    }

    @Operation(summary = "Escalader un dossier vers une phase supérieure")
    @PutMapping("/dossiers/{uid}/escalader")
    public ResponseEntity<ApiResponse<DossierRecouvrementResponse>> escalader(
            @PathVariable UUID uid,
            @Valid @RequestBody EscaladerDossierRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok("Phase mise à jour.",
                recouvrementService.escalader(uid, request, user)));
    }

    @Operation(summary = "Clôturer un dossier (paiement, radiation ou accord)")
    @PutMapping("/dossiers/{uid}/clore")
    public ResponseEntity<ApiResponse<DossierRecouvrementResponse>> clore(
            @PathVariable UUID uid,
            @RequestParam(defaultValue = "") String motif,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok("Dossier clôturé.",
                recouvrementService.clore(uid, motif, user)));
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    @Operation(summary = "Enregistrer une action (appel, SMS, visite, MoMo, huissier, médiation…)")
    @PostMapping("/dossiers/{uid}/actions")
    public ResponseEntity<ApiResponse<ActionRecouvrementResponse>> ajouterAction(
            @PathVariable UUID uid,
            @Valid @RequestBody AjouterActionRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Action enregistrée.",
                        recouvrementService.ajouterAction(uid, request, user)));
    }

    @Operation(summary = "Historique des actions d'un dossier")
    @GetMapping("/dossiers/{uid}/actions")
    public ResponseEntity<ApiResponse<List<ActionRecouvrementResponse>>> getActions(@PathVariable UUID uid) {
        return ResponseEntity.ok(ApiResponse.ok(recouvrementService.getActions(uid)));
    }

    // ── Accords de rééchelonnement ────────────────────────────────────────────

    @Operation(summary = "Créer un accord de rééchelonnement formel (bascule automatiquement en phase REECHELONNEMENT)")
    @PostMapping("/dossiers/{uid}/accords")
    public ResponseEntity<ApiResponse<AccordReechelonnementResponse>> creerAccord(
            @PathVariable UUID uid,
            @Valid @RequestBody AccordReechelonnementRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Accord de rééchelonnement enregistré.",
                        recouvrementService.creerAccord(uid, request, user)));
    }

    @Operation(summary = "Liste des accords de rééchelonnement d'un dossier")
    @GetMapping("/dossiers/{uid}/accords")
    public ResponseEntity<ApiResponse<List<AccordReechelonnementResponse>>> getAccords(@PathVariable UUID uid) {
        return ResponseEntity.ok(ApiResponse.ok(recouvrementService.getAccords(uid)));
    }

    // ── Guard tenant ──────────────────────────────────────────────────────────

    private void requireTenant(User user) {
        if (user.getImf() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Les endpoints recouvrement ne sont pas accessibles au SUPER_ADMIN.");
        }
    }
}
