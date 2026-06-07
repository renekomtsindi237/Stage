package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.AccordReechelonnementRequest;
import cm.imf.pipeline.dto.request.AjouterActionRequest;
import cm.imf.pipeline.dto.request.EscaladerDossierRequest;
import cm.imf.pipeline.dto.request.OuvrirDossierRequest;
import cm.imf.pipeline.dto.response.AccordReechelonnementResponse;
import cm.imf.pipeline.dto.response.ActionRecouvrementResponse;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.DossierRecouvrementResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.RecouvrementPhase;
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

import java.util.List;
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
