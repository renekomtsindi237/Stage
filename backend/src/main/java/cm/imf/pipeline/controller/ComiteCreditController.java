package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.DecisionComiteRequest;
import cm.imf.pipeline.dto.request.OuvrirSeanceComiteRequest;
import cm.imf.pipeline.dto.request.VoterComiteRequest;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.ComiteDecisionResponse;
import cm.imf.pipeline.dto.response.DossierCreditResponse;
import cm.imf.pipeline.dto.response.VoteComiteResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.service.ICreditService;
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

@RestController
@RequestMapping("/api/v1/comite")
@RequiredArgsConstructor
@Tag(name = "Comité de Crédit", description = "Workflow de vote collégial — approbation, rejet, ajournement")
public class ComiteCreditController {

    private final ICreditService creditService;

    @Operation(summary = "Ouvrir une séance de comité pour un dossier")
    @PostMapping("/dossier/{dossierUid}/seance")
    public ResponseEntity<ApiResponse<ComiteDecisionResponse>> ouvrirSeance(
            @PathVariable UUID dossierUid,
            @Valid @RequestBody OuvrirSeanceComiteRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Séance ouverte.",
                        creditService.ouvrirSeance(dossierUid, request, user)));
    }

    @Operation(summary = "Voter (POUR / CONTRE / ABSTENTION)")
    @PostMapping("/dossier/{dossierUid}/vote")
    public ResponseEntity<ApiResponse<VoteComiteResponse>> voter(
            @PathVariable UUID dossierUid,
            @Valid @RequestBody VoterComiteRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Vote enregistré.",
                        creditService.voter(dossierUid, request, user)));
    }

    @Operation(summary = "Enregistrer la décision finale du comité")
    @PostMapping("/dossier/{dossierUid}/decision")
    public ResponseEntity<ApiResponse<DossierCreditResponse>> enregistrerDecision(
            @PathVariable UUID dossierUid,
            @Valid @RequestBody DecisionComiteRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok("Décision enregistrée.",
                creditService.enregistrerDecision(dossierUid, request, user)));
    }

    @Operation(summary = "Historique des séances de comité d'un dossier")
    @GetMapping("/dossier/{dossierUid}/seances")
    public ResponseEntity<ApiResponse<List<ComiteDecisionResponse>>> listSeances(@PathVariable UUID dossierUid) {
        return ResponseEntity.ok(ApiResponse.ok(creditService.listComites(dossierUid)));
    }

    @Operation(summary = "Votes d'une séance de comité")
    @GetMapping("/seance/{comiteUid}/votes")
    public ResponseEntity<ApiResponse<List<VoteComiteResponse>>> listVotes(@PathVariable UUID comiteUid) {
        return ResponseEntity.ok(ApiResponse.ok(creditService.listVotes(comiteUid)));
    }
}
