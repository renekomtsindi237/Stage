package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.dto.response.PretResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.service.IPretService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/prets")
@RequiredArgsConstructor
@Tag(name = "Prêts", description = "Consultation des prêts depuis le pipeline staging")
public class PretController {

    private final IPretService pretService;

    @Operation(summary = "Liste paginée des prêts, filtrée par statut optionnel")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PretResponse>>> listPrets(
            @Parameter(description = "Statut : ACTIF, EN_RETARD, EN_RECOUVREMENT, SOLDE, PERTE")
            @RequestParam(required = false) String statut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<PretResponse> data = pretService.listPrets(statut, page, size);
        long total              = pretService.countPrets(statut);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(data, page, size, total)));
    }

    @Operation(summary = "Détail d'un prêt par son identifiant")
    @GetMapping("/{idPret}")
    public ResponseEntity<ApiResponse<PretResponse>> getById(@PathVariable String idPret) {
        return ResponseEntity.ok(ApiResponse.ok(pretService.getById(idPret)));
    }

    @Operation(summary = "Prêts d'un client spécifique")
    @GetMapping("/client/{idClient}")
    public ResponseEntity<ApiResponse<List<PretResponse>>> getPretsClient(
            @PathVariable String idClient) {
        return ResponseEntity.ok(ApiResponse.ok(pretService.getPretsClient(idClient)));
    }

    @Operation(summary = "Prêts de l'agent connecté (app mobile)")
    @GetMapping("/mes-prets")
    public ResponseEntity<ApiResponse<List<PretResponse>>> getMesPrets(
            @AuthenticationPrincipal User agent) {
        return ResponseEntity.ok(ApiResponse.ok(pretService.getPretsAgent(agent.getUsername())));
    }
}
