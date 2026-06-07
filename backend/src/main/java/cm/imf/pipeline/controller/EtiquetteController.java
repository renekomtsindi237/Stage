package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.EtiquetteRequest;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.EtiquetteResponse;
import cm.imf.pipeline.entity.AuditTrail;
import cm.imf.pipeline.entity.EtiquetteDossier;
import cm.imf.pipeline.exception.ResourceNotFoundException;
import cm.imf.pipeline.repository.EtiquetteDossierRepository;
import cm.imf.pipeline.security.Auditable;
import cm.imf.pipeline.security.TenantContext;
import cm.imf.pipeline.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestion des étiquettes apposées sur les dossiers de recouvrement.
 * Traçabilité complète : qui a posé / retiré quelle étiquette, quand.
 */
@RestController
@RequestMapping("/dossiers")
@RequiredArgsConstructor
@Tag(name = "Étiquettes", description = "Classification et traçabilité des dossiers")
public class EtiquetteController {

    private final EtiquetteDossierRepository etiquetteRepository;

    @Operation(summary = "Étiquettes actives d'un dossier")
    @GetMapping("/{dossierRef}/etiquettes")
    @PreAuthorize("hasAnyRole('RESPONSABLE_RECOUVREMENT', 'DIRECTEUR', 'DSI', 'SUPER_ADMIN', 'ANALYSTE')")
    public ResponseEntity<ApiResponse<List<EtiquetteResponse>>> lister(
            @PathVariable String dossierRef) {

        Long imfId = TenantContext.currentImfId();
        List<EtiquetteResponse> etiquettes = etiquetteRepository
                .findByImfIdAndDossierRefAndActiveTrue(imfId, dossierRef)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(etiquettes));
    }

    @Operation(summary = "Poser une étiquette sur un dossier")
    @PostMapping("/{dossierRef}/etiquettes")
    @PreAuthorize("hasAnyRole('RESPONSABLE_RECOUVREMENT', 'DSI', 'SUPER_ADMIN')")
    @Auditable(action = AuditTrail.ACTION_CREATION, entiteType = AuditTrail.ENTITE_ETIQUETTE,
               entiteIdExpression = "#dossierRef", motifExpression = "#req.motif",
               captureResult = false)
    public ResponseEntity<ApiResponse<EtiquetteResponse>> poser(
            @PathVariable String dossierRef,
            @Valid @RequestBody EtiquetteRequest req) {

        Long imfId = TenantContext.currentImfId();
        User moi   = TenantContext.currentUser();

        // Idempotent : ne pas dupliquer la même étiquette active
        if (etiquetteRepository.existsByImfIdAndDossierRefAndCodeEtiquetteAndActiveTrue(
                imfId, dossierRef, req.codeEtiquette())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Étiquette déjà active sur ce dossier"));
        }

        EtiquetteDossier etiquette = EtiquetteDossier.builder()
                .imfId(imfId)
                .dossierRef(dossierRef)
                .dossierType(req.dossierType() != null ? req.dossierType() : "DOSSIER_RECOUVREMENT")
                .codeEtiquette(req.codeEtiquette())
                .couleur(req.couleur())
                .libelleCustom(req.libelleCustom())
                .commentaire(req.commentaire())
                .posePar_Id(moi.getId())
                .poseParUsername(moi.getUsername())
                .build();

        etiquette = etiquetteRepository.save(etiquette);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toResponse(etiquette)));
    }

    @Operation(summary = "Retirer une étiquette d'un dossier",
               description = "Marque l'étiquette comme inactive (conservation pour traçabilité).")
    @DeleteMapping("/{dossierRef}/etiquettes/{uid}")
    @PreAuthorize("hasAnyRole('RESPONSABLE_RECOUVREMENT', 'DSI', 'SUPER_ADMIN')")
    @Auditable(action = AuditTrail.ACTION_SUPPRESSION, entiteType = AuditTrail.ENTITE_ETIQUETTE,
               entiteIdExpression = "#uid.toString()")
    public ResponseEntity<ApiResponse<Void>> retirer(
            @PathVariable String dossierRef,
            @PathVariable UUID uid,
            @RequestParam(required = false) String motif) {

        Long imfId = TenantContext.currentImfId();
        User moi   = TenantContext.currentUser();

        EtiquetteDossier etiquette = etiquetteRepository.findByUid(uid)
                .filter(e -> e.getImfId().equals(imfId) && e.getDossierRef().equals(dossierRef))
                .orElseThrow(() -> new ResourceNotFoundException("EtiquetteDossier", uid));

        etiquette.setActive(false);
        etiquette.setDateRetrait(OffsetDateTime.now());
        etiquette.setRetirePar_Id(moi.getId());
        etiquette.setRetireParUsername(moi.getUsername());
        etiquette.setCommentaire(
                etiquette.getCommentaire() != null
                ? etiquette.getCommentaire() + " | Retrait: " + motif
                : motif);
        etiquetteRepository.save(etiquette);

        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "Dossiers étiquetés par code",
               description = "Liste des références dossiers portant une étiquette donnée.")
    @GetMapping("/etiquettes/{codeEtiquette}")
    @PreAuthorize("hasAnyRole('RESPONSABLE_RECOUVREMENT', 'DIRECTEUR', 'DSI', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listerParCode(
            @PathVariable String codeEtiquette) {

        Long imfId = TenantContext.currentImfId();
        List<Map<String, Object>> result = etiquetteRepository
                .findByImfIdAndCodeEtiquetteAndActiveTrue(imfId, codeEtiquette)
                .stream()
                .map(e -> Map.<String, Object>of(
                        "dossierRef",     e.getDossierRef(),
                        "dossierType",    e.getDossierType(),
                        "commentaire",    e.getCommentaire() != null ? e.getCommentaire() : "",
                        "poseParUsername", e.getPoseParUsername(),
                        "dateDebut",      e.getDateDebut()
                ))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private EtiquetteResponse toResponse(EtiquetteDossier e) {
        return new EtiquetteResponse(
                e.getUid() != null ? e.getUid().toString() : null,
                e.getDossierRef(),
                e.getDossierType(),
                e.getCodeEtiquette(),
                e.getCouleur(),
                e.getLibelleCustom(),
                e.getCommentaire(),
                e.isActive(),
                e.getDateDebut(),
                e.getDateFin(),
                e.getPoseParUsername(),
                e.getRetireParUsername(),
                e.getDateRetrait(),
                e.getCreatedAt()
        );
    }
}
