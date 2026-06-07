package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.*;
import cm.imf.pipeline.dto.response.*;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.NiveauKyc;
import cm.imf.pipeline.enums.NiveauRisque;
import cm.imf.pipeline.enums.StatutKyc;
import cm.imf.pipeline.service.IKycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * KYC multi-niveaux — conformité COBAC/BEAC — Cameroun
 *
 * Niveaux :
 *   NIVEAU_1 — identité de base (CNI + biographie)         → &lt; 150 000 FCFA/mois
 *   NIVEAU_2 — identité renforcée + domicile + activité    → usage standard
 *   NIVEAU_3 — diligence renforcée PPE/LBC/FT              → risque élevé
 *
 * Référence : Règlement COBAC R-2005/01 · Loi N°2003/008 · Directives BEAC
 */
@RestController
@RequestMapping("/kyc")
@RequiredArgsConstructor
@Tag(name = "KYC", description = "Know Your Customer multi-niveaux — COBAC/BEAC")
public class KycController {

    private final IKycService kycService;

    // ── Dossiers ──────────────────────────────────────────────────────────────

    @Operation(summary = "Initier un dossier KYC pour un client")
    @PostMapping("/dossiers")
    public ResponseEntity<ApiResponse<KycDossierResponse>> initierDossier(
            @Valid @RequestBody InitierKycRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Dossier KYC initié.", kycService.initierDossier(request, user)));
    }

    @Operation(summary = "Liste paginée des dossiers (filtres : statut, niveau, risque)")
    @GetMapping("/dossiers")
    public ResponseEntity<ApiResponse<PageResponse<KycDossierResponse>>> listDossiers(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) StatutKyc statut,
            @RequestParam(required = false) NiveauKyc niveau,
            @RequestParam(required = false) NiveauRisque risque,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Long imfId = requireImfId(user);
        return ResponseEntity.ok(ApiResponse.ok(
                kycService.listDossiers(imfId, statut, niveau, risque, page, size)));
    }

    @Operation(summary = "Détail d'un dossier KYC")
    @GetMapping("/dossiers/{uid}")
    public ResponseEntity<ApiResponse<KycDossierResponse>> getDossier(@PathVariable UUID uid) {
        return ResponseEntity.ok(ApiResponse.ok(kycService.getDossier(uid)));
    }

    @Operation(summary = "Évaluer / mettre à jour le score de risque LBC/FT (PPE, sanctions, listes noires)")
    @PutMapping("/dossiers/{uid}/risque")
    public ResponseEntity<ApiResponse<KycDossierResponse>> evaluerRisque(
            @PathVariable UUID uid,
            @Valid @RequestBody EvaluerRisqueKycRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok("Risque évalué.", kycService.evaluerRisque(uid, request, user)));
    }

    @Operation(summary = "Décision de vérification : APPROUVE / REJETE / COMPLEMENT_REQUIS")
    @PutMapping("/dossiers/{uid}/verifier")
    public ResponseEntity<ApiResponse<KycDossierResponse>> verifier(
            @PathVariable UUID uid,
            @Valid @RequestBody VerifierKycRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok("Vérification enregistrée.", kycService.verifier(uid, request, user)));
    }

    @Operation(summary = "Historique des décisions de vérification (audit trail)")
    @GetMapping("/dossiers/{uid}/verifications")
    public ResponseEntity<ApiResponse<List<KycVerificationResponse>>> getVerifications(@PathVariable UUID uid) {
        return ResponseEntity.ok(ApiResponse.ok(kycService.getHistoriqueVerifications(uid)));
    }

    // ── Documents ─────────────────────────────────────────────────────────────

    @Operation(summary = "Soumettre un document (base64) pour un dossier KYC")
    @PostMapping("/dossiers/{dossierUid}/documents")
    public ResponseEntity<ApiResponse<KycDocumentResponse>> soumettreDocument(
            @PathVariable UUID dossierUid,
            @Valid @RequestBody SoumettreDocumentKycRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Document soumis.", kycService.soumettreDocument(dossierUid, request, user)));
    }

    @Operation(summary = "Liste des documents d'un dossier")
    @GetMapping("/dossiers/{dossierUid}/documents")
    public ResponseEntity<ApiResponse<List<KycDocumentResponse>>> getDocuments(@PathVariable UUID dossierUid) {
        return ResponseEntity.ok(ApiResponse.ok(kycService.getDocuments(dossierUid)));
    }

    @Operation(summary = "Valider ou rejeter un document individuel")
    @PutMapping("/documents/{documentUid}/valider")
    public ResponseEntity<ApiResponse<KycDocumentResponse>> validerDocument(
            @PathVariable UUID documentUid,
            @Valid @RequestBody ValiderDocumentKycRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok("Document traité.", kycService.validerDocument(documentUid, request, user)));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Long requireImfId(User user) {
        if (user.getImf() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Accès réservé aux utilisateurs d'une IMF.");
        }
        return user.getImf().getId();
    }
}
