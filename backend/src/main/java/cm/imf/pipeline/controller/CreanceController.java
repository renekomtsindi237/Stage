package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.CreanceResponse;
import cm.imf.pipeline.dto.response.KpiRecouvrementResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.repository.AgenceRepository;
import cm.imf.pipeline.service.ICreanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/creances")
@RequiredArgsConstructor
@Tag(name = "Créances", description = "Recouvrement de créances — PAR, provisions COBAC, scoring MCRS")
public class CreanceController {

    private final ICreanceService  service;
    private final AgenceRepository agenceRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('DIRECTEUR','RESPONSABLE_RECOUVREMENT','ANALYSTE','DSI','SUPER_ADMIN')")
    @Operation(summary = "Liste des créances avec filtres")
    public ResponseEntity<ApiResponse<PageResponse<CreanceResponse>>> lister(
            @RequestParam(required = false) UUID agenceUid,
            @RequestParam(required = false) String categoriePar,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long agenceId = agenceUid == null ? null
                : agenceRepository.findByUid(agenceUid).map(a -> a.getId()).orElse(null);
        return ResponseEntity.ok(ApiResponse.ok(
            service.lister(null, agenceId, categoriePar, statut, dateDebut, dateFin, page, size)
        ));
    }

    @GetMapping("/{uid}")
    @PreAuthorize("hasAnyRole('DIRECTEUR','RESPONSABLE_RECOUVREMENT','ANALYSTE','DSI','SUPER_ADMIN')")
    @Operation(summary = "Détail d'une créance avec historique recouvrement")
    public ResponseEntity<ApiResponse<CreanceResponse>> detail(@PathVariable UUID uid) {
        return ResponseEntity.ok(ApiResponse.ok(service.detail(uid)));
    }

    @GetMapping("/kpi")
    @PreAuthorize("hasAnyRole('DIRECTEUR','RESPONSABLE_RECOUVREMENT','DSI','SUPER_ADMIN')")
    @Operation(summary = "KPI recouvrement : PAR, provisions COBAC, benchmarks")
    public ResponseEntity<ApiResponse<KpiRecouvrementResponse>> kpi(
            @RequestParam(required = false) UUID agenceUid,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datePeriode) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.kpiRecouvrement(null, agenceUid, datePeriode != null ? datePeriode : LocalDate.now())
        ));
    }

    @GetMapping("/client/{clientId}/score-mcrs")
    @PreAuthorize("hasAnyRole('DIRECTEUR','RESPONSABLE_RECOUVREMENT','SUPER_ADMIN')")
    @Operation(summary = "Score MCRS le plus récent pour un client (depuis ml.client_scores)")
    public ResponseEntity<ApiResponse<CreanceResponse.ScoreMcrs>> scoreClient(
            @PathVariable String clientId) {
        return ResponseEntity.ok(ApiResponse.ok(service.scoreClient(null, clientId)));
    }

    @PatchMapping("/{uid}/statut")
    @PreAuthorize("hasAnyRole('DIRECTEUR','RESPONSABLE_RECOUVREMENT','SUPER_ADMIN')")
    @Operation(summary = "Mettre à jour le statut d'une créance")
    public ResponseEntity<ApiResponse<CreanceResponse>> majStatut(
            @PathVariable UUID uid,
            @RequestParam String statut,
            @RequestParam(required = false) String observation) {
        return ResponseEntity.ok(ApiResponse.ok(service.majStatut(uid, statut, observation)));
    }
}
