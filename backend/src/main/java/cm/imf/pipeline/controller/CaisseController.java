package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.EncaissementRequest;
import cm.imf.pipeline.dto.request.ExecuterDecaissementRequest;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.DecaissementResponse;
import cm.imf.pipeline.dto.response.OperationCaisseResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.service.IBackOfficeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/caisse")
@RequiredArgsConstructor
@Tag(name = "Caisse", description = "Décaissements et encaissements — CAISSIER")
public class CaisseController {

    private final IBackOfficeService backOfficeService;

    @Operation(summary = "Exécuter un décaissement sur ordre de contrat SIGNE")
    @PostMapping("/decaissements")
    public ResponseEntity<ApiResponse<DecaissementResponse>> decaissement(
            @Valid @RequestBody ExecuterDecaissementRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Décaissement exécuté.",
                        backOfficeService.executerDecaissement(request, user)));
    }

    @Operation(summary = "Enregistrer un encaissement (remboursement échéance)")
    @PostMapping("/encaissements")
    public ResponseEntity<ApiResponse<OperationCaisseResponse>> encaissement(
            @Valid @RequestBody EncaissementRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Encaissement enregistré.",
                        backOfficeService.enregistrerEncaissement(request, user)));
    }

    @Operation(summary = "Journal de caisse paginé (toutes opérations de l'IMF)")
    @GetMapping("/journal")
    public ResponseEntity<ApiResponse<PageResponse<OperationCaisseResponse>>> journal(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(backOfficeService.journalCaisse(user, page, size)));
    }
}
