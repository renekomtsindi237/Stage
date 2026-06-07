package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.CollecteEpargneRequest;
import cm.imf.pipeline.dto.request.SyncCollectesRequest;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.CollecteEpargneResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.dto.response.SyncCollectesResponse;
import cm.imf.pipeline.repository.AgenceRepository;
import cm.imf.pipeline.repository.UserRepository;
import cm.imf.pipeline.service.ICollecteEpargneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/collectes-epargne")
@RequiredArgsConstructor
@Tag(name = "Collectes Épargne", description = "Gestion des collectes d'épargne terrain")
public class CollecteEpargneController {

    private final ICollecteEpargneService service;
    private final AgenceRepository        agenceRepository;
    private final UserRepository          userRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('AGENT','SUPER_ADMIN')")
    @Operation(summary = "Soumettre une collecte d'épargne")
    public ResponseEntity<ApiResponse<CollecteEpargneResponse>> soumettre(
            @Valid @RequestBody CollecteEpargneRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.soumettre(request)));
    }

    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('AGENT','SUPER_ADMIN')")
    @Operation(summary = "Synchronisation batch (offline-first) — déduplication UUID")
    public ResponseEntity<ApiResponse<SyncCollectesResponse>> syncBatch(
            @Valid @RequestBody SyncCollectesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.syncBatch(request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DIRECTEUR','RESPONSABLE_RECOUVREMENT','ANALYSTE','DSI','SUPER_ADMIN')")
    @Operation(summary = "Lister les collectes avec filtres et pagination")
    public ResponseEntity<ApiResponse<PageResponse<CollecteEpargneResponse>>> lister(
            @RequestParam(required = false) UUID agenceUid,
            @RequestParam(required = false) UUID agentUid,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String statut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long agenceId = agenceUid == null ? null
                : agenceRepository.findByUid(agenceUid).map(a -> a.getId()).orElse(null);
        Long agentId = agentUid == null ? null
                : userRepository.findByUid(agentUid).map(u -> u.getId()).orElse(null);
        return ResponseEntity.ok(ApiResponse.ok(
            service.lister(null, agenceId, agentId, dateDebut, dateFin, statut, page, size)
        ));
    }

    @PatchMapping("/{uid}/valider")
    @PreAuthorize("hasAnyRole('DIRECTEUR','RESPONSABLE_RECOUVREMENT','SUPER_ADMIN')")
    @Operation(summary = "Valider ou rejeter une collecte")
    public ResponseEntity<ApiResponse<CollecteEpargneResponse>> valider(
            @PathVariable UUID uid,
            @RequestParam(required = false) String motifRejet) {
        return ResponseEntity.ok(ApiResponse.ok(service.valider(uid, motifRejet)));
    }

    @GetMapping("/mon-kpi-jour")
    @PreAuthorize("hasRole('AGENT')")
    @Operation(summary = "KPI du jour pour l'agent connecté")
    public ResponseEntity<ApiResponse<CollecteEpargneResponse.KpiJour>> kpiJour(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.kpiJour(null, date != null ? date : LocalDate.now())
        ));
    }

    @GetMapping("/non-synchros")
    @PreAuthorize("hasRole('AGENT')")
    @Operation(summary = "Collectes en attente de synchronisation")
    public ResponseEntity<ApiResponse<List<CollecteEpargneResponse>>> collectesNonSynchros() {
        return ResponseEntity.ok(ApiResponse.ok(service.collectesNonSynchros(null)));
    }
}
