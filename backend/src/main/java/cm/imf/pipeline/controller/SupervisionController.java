package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.*;
import cm.imf.pipeline.entity.Imf;
import cm.imf.pipeline.exception.ResourceNotFoundException;
import cm.imf.pipeline.repository.AgenceRepository;
import cm.imf.pipeline.repository.ImfRepository;
import cm.imf.pipeline.repository.JournalAuditRepository;
import cm.imf.pipeline.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints de supervision système réservés au SUPER_ADMIN.
 * Lecture seule sur toutes les données de toutes les IMF.
 * Les DSI conservent les droits d'écriture sur leurs propres données.
 */
@RestController
@RequestMapping("/platform/supervision")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Supervision", description = "Lecture système globale — SUPER_ADMIN uniquement")
public class SupervisionController {

    private final UserRepository         userRepository;
    private final AgenceRepository       agenceRepository;
    private final JournalAuditRepository auditRepository;
    private final ImfRepository          imfRepository;

    // ── Utilisateurs d'une IMF ────────────────────────────────────────────────

    @Operation(summary = "Lister les utilisateurs d'une IMF (lecture seule)")
    @GetMapping("/imf/{imfUid}/users")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getUsersByImf(
            @PathVariable UUID imfUid,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Long imfId = resolveImfId(imfUid);
        Page<UserResponse> users = userRepository
                .findByImfId(imfId, PageRequest.of(page, size, Sort.by("username")))
                .map(UserResponse::from);
        return ResponseEntity.ok(ApiResponse.ok(users));
    }

    // ── Agences d'une IMF ─────────────────────────────────────────────────────

    @Operation(summary = "Lister les agences d'une IMF")
    @GetMapping("/imf/{imfUid}/agences")
    public ResponseEntity<ApiResponse<List<AgenceResponse>>> getAgencesByImf(
            @PathVariable UUID imfUid) {

        Long imfId = resolveImfId(imfUid);
        List<AgenceResponse> agences = agenceRepository
                .findByImfIdOrderByNomAsc(imfId)
                .stream().map(AgenceResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.ok(agences));
    }

    // ── Audit d'une IMF ───────────────────────────────────────────────────────

    @Operation(summary = "Journal d'audit d'une IMF (lecture seule)")
    @GetMapping("/imf/{imfUid}/audit")
    public ResponseEntity<ApiResponse<Page<AuditEntryResponse>>> getAuditByImf(
            @PathVariable UUID imfUid,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        Long imfId = resolveImfId(imfUid);
        Page<AuditEntryResponse> audit = auditRepository
                .findByImfId(imfId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(AuditEntryResponse::from);
        return ResponseEntity.ok(ApiResponse.ok(audit));
    }

    // ── Journal global (toutes IMF confondues) ────────────────────────────────

    @Operation(summary = "Journal d'audit global — toutes les IMF")
    @GetMapping("/audit")
    public ResponseEntity<ApiResponse<Page<AuditEntryResponse>>> getGlobalAudit(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<AuditEntryResponse> audit = auditRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                .map(AuditEntryResponse::from);
        return ResponseEntity.ok(ApiResponse.ok(audit));
    }

    // ── Résumé statistique d'une IMF ──────────────────────────────────────────

    @Operation(summary = "Résumé chiffré d'une IMF (utilisateurs, agences)")
    @GetMapping("/imf/{imfUid}/summary")
    public ResponseEntity<ApiResponse<ImfSummaryResponse>> getImfSummary(
            @PathVariable UUID imfUid) {

        Long imfId = resolveImfId(imfUid);
        long userCount   = userRepository.countByImfId(imfId);
        long agenceCount = agenceRepository.findByImfIdOrderByNomAsc(imfId).size();
        return ResponseEntity.ok(ApiResponse.ok(new ImfSummaryResponse(imfUid.toString(), userCount, agenceCount)));
    }

    public record ImfSummaryResponse(String imfUid, long userCount, long agenceCount) {}

    // ── Helper ────────────────────────────────────────────────────────────────

    private Long resolveImfId(UUID imfUid) {
        Imf imf = imfRepository.findByUid(imfUid)
                .orElseThrow(() -> new ResourceNotFoundException("IMF", imfUid));
        return imf.getId();
    }
}
