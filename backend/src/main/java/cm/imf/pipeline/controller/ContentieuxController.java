package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.entity.*;
import cm.imf.pipeline.repository.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/contentieux")
@RequiredArgsConstructor
@Tag(name = "Contentieux OHADA", description = "Procédures judiciaires OHADA — RESPONSABLE_RECOUVREMENT")
public class ContentieuxController {

    private final ProcedureContentieuxRepository procedureRepo;
    private final IntervenantJudiaireRepository intervenantRepo;
    private final ActionContentieuxRepository actionRepo;
    private final RecouvrementDossierRepository dossierRepo;

    // ── Procédures ────────────────────────────────────────────────────────────

    @Operation(summary = "Ouvrir une procédure OHADA pour un dossier de recouvrement")
    @PostMapping("/dossier/{dossierUid}/procedures")
    public ResponseEntity<ApiResponse<Map<String, Object>>> ouvrirProcedure(
            @PathVariable UUID dossierUid,
            @RequestParam @NotBlank String typeProcedure,
            @RequestParam(required = false) String juridiction,
            @RequestParam(required = false) BigDecimal montantReclame,
            @RequestParam(required = false) LocalDate dateSaisine,
            @AuthenticationPrincipal User user) {
        RecouvrementDossier dossier = dossierRepo.findByUid(dossierUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dossier de recouvrement introuvable."));
        ProcedureContentieux proc = ProcedureContentieux.builder()
                .dossierId(dossier.getId())
                .typeProcedure(typeProcedure)
                .juridiction(juridiction)
                .montantReclame(montantReclame)
                .dateSaisine(dateSaisine)
                .responsableId(user.getId())
                .build();
        ProcedureContentieux saved = procedureRepo.save(proc);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Procédure ouverte.", Map.of(
                        "uid", saved.getUid(),
                        "statut", saved.getStatut(),
                        "typeProcedure", saved.getTypeProcedure())));
    }

    @Operation(summary = "Liste des procédures d'un dossier de recouvrement")
    @GetMapping("/dossier/{dossierUid}/procedures")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listProcedures(@PathVariable UUID dossierUid) {
        RecouvrementDossier dossier = dossierRepo.findByUid(dossierUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dossier de recouvrement introuvable."));
        List<Map<String, Object>> result = procedureRepo.findByDossierIdOrderByCreatedAtDesc(dossier.getId())
                .stream().map(p -> Map.<String, Object>of(
                        "uid", p.getUid(),
                        "typeProcedure", p.getTypeProcedure(),
                        "statut", p.getStatut(),
                        "juridiction", p.getJuridiction() != null ? p.getJuridiction() : "",
                        "montantReclame", p.getMontantReclame() != null ? p.getMontantReclame() : BigDecimal.ZERO,
                        "createdAt", p.getCreatedAt()
                )).toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "Mettre à jour le statut d'une procédure")
    @PatchMapping("/procedures/{procedureUid}/statut")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateStatut(
            @PathVariable UUID procedureUid,
            @RequestParam @NotBlank String statut,
            @AuthenticationPrincipal User user) {
        ProcedureContentieux proc = procedureRepo.findByUid(procedureUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Procédure introuvable."));
        proc.setStatut(statut.toUpperCase());
        procedureRepo.save(proc);
        return ResponseEntity.ok(ApiResponse.ok("Statut mis à jour.",
                Map.of("uid", proc.getUid(), "statut", proc.getStatut())));
    }

    // ── Intervenants ──────────────────────────────────────────────────────────

    @Operation(summary = "Ajouter un intervenant judiciaire (huissier, avocat, etc.)")
    @PostMapping("/procedures/{procedureUid}/intervenants")
    public ResponseEntity<ApiResponse<Map<String, Object>>> ajouterIntervenant(
            @PathVariable UUID procedureUid,
            @RequestParam @NotBlank String type,
            @RequestParam @NotBlank String nom,
            @RequestParam(required = false) String referenceMission,
            @RequestParam(required = false) BigDecimal honoraires) {
        ProcedureContentieux proc = procedureRepo.findByUid(procedureUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Procédure introuvable."));
        IntervenantJudiciaire intervenant = IntervenantJudiciaire.builder()
                .procedureId(proc.getId())
                .type(type)
                .nom(nom)
                .referenceMission(referenceMission)
                .honoraires(honoraires)
                .build();
        IntervenantJudiciaire saved = intervenantRepo.save(intervenant);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Intervenant ajouté.", Map.of(
                        "id", saved.getId(), "type", saved.getType(), "nom", saved.getNom())));
    }

    @Operation(summary = "Intervenants d'une procédure")
    @GetMapping("/procedures/{procedureUid}/intervenants")
    public ResponseEntity<ApiResponse<List<IntervenantJudiciaire>>> listIntervenants(
            @PathVariable UUID procedureUid) {
        ProcedureContentieux proc = procedureRepo.findByUid(procedureUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Procédure introuvable."));
        return ResponseEntity.ok(ApiResponse.ok(intervenantRepo.findByProcedureId(proc.getId())));
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    @Operation(summary = "Enregistrer une action contentieux (audience, saisie, vente...)")
    @PostMapping("/procedures/{procedureUid}/actions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> ajouterAction(
            @PathVariable UUID procedureUid,
            @RequestParam @NotBlank String typeAction,
            @RequestParam(required = false) LocalDate dateAction,
            @RequestParam(required = false) String resultat,
            @RequestParam(required = false) BigDecimal montantRecouvre) {
        ProcedureContentieux proc = procedureRepo.findByUid(procedureUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Procédure introuvable."));
        ActionContentieux action = ActionContentieux.builder()
                .procedureId(proc.getId())
                .typeAction(typeAction)
                .dateAction(dateAction)
                .resultat(resultat)
                .montantRecouvre(montantRecouvre)
                .build();
        ActionContentieux saved = actionRepo.save(action);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Action enregistrée.",
                        Map.of("id", saved.getId(), "typeAction", saved.getTypeAction())));
    }

    @Operation(summary = "Actions d'une procédure contentieux")
    @GetMapping("/procedures/{procedureUid}/actions")
    public ResponseEntity<ApiResponse<List<ActionContentieux>>> listActions(@PathVariable UUID procedureUid) {
        ProcedureContentieux proc = procedureRepo.findByUid(procedureUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Procédure introuvable."));
        return ResponseEntity.ok(ApiResponse.ok(
                actionRepo.findByProcedureIdOrderByDateActionDesc(proc.getId())));
    }
}
