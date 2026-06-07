package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.CollecteRequest;
import cm.imf.pipeline.dto.response.CollecteResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.service.ICollecteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/collectes")
@RequiredArgsConstructor
@Tag(name = "Collectes", description = "Saisie et consultation des collectes terrain")
public class CollecteController {

    private final ICollecteService collecteService;

    @Operation(summary = "Enregistrer une collecte terrain (rôle AGENT)")
    @PostMapping
    public ResponseEntity<CollecteResponse> enregistrer(
            @Valid @RequestBody CollecteRequest request,
            @AuthenticationPrincipal User agent) {
        CollecteResponse response = collecteService.enregistrer(request, agent);
        HttpStatus status = switch (response.statut()) {
            case CONFIRMEE -> HttpStatus.CREATED;
            case DOUBLON   -> HttpStatus.CONFLICT;
            default        -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(response);
    }

    @Operation(summary = "Mes collectes — liste paginée pour l'agent connecté")
    @GetMapping("/mes-collectes")
    public ResponseEntity<PageResponse<CollecteResponse>> getMesCollectes(
            @AuthenticationPrincipal User agent,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(collecteService.getMesCollectes(agent, page, size));
    }

    @Operation(summary = "Détail d'une collecte par uid")
    @GetMapping("/{uid}")
    public ResponseEntity<CollecteResponse> getById(@PathVariable UUID uid) {
        return ResponseEntity.ok(collecteService.getById(uid));
    }
}
