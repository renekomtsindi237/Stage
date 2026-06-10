package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.DelegateUserRequest;
import cm.imf.pipeline.dto.request.UpdateUserRequest;
import cm.imf.pipeline.dto.response.*;
import cm.imf.pipeline.entity.Imf;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.exception.BusinessException;
import cm.imf.pipeline.exception.ResourceNotFoundException;
import cm.imf.pipeline.repository.AgenceRepository;
import cm.imf.pipeline.repository.ImfRepository;
import cm.imf.pipeline.repository.JournalAuditRepository;
import cm.imf.pipeline.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
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

    @Operation(summary = "Lister les utilisateurs d'une IMF")
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

    @Operation(summary = "Modifier un utilisateur d'une IMF")
    @Transactional
    @PatchMapping("/imf/{imfUid}/users/{userUid}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable UUID imfUid,
            @PathVariable UUID userUid,
            @Valid @RequestBody UpdateUserRequest request) {

        Long imfId = resolveImfId(imfUid);
        User user = resolveUser(userUid, imfId);

        if (request.role() == Role.SUPER_ADMIN) {
            throw new BusinessException("Le rôle SUPER_ADMIN ne peut pas être assigné à un utilisateur IMF.");
        }
        // Si le nouveau rôle est DSI, vérifier qu'il n'en existe pas déjà un autre
        if (request.role() == Role.DSI && user.getRole() != Role.DSI) {
            boolean dsiExists = userRepository.existsByImfIdAndRole(imfId, Role.DSI);
            if (dsiExists) {
                throw new BusinessException("Un DSI existe déjà pour cette IMF. Reléguer d'abord le DSI actuel.");
            }
        }

        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setRole(request.role());
        user.setZoneId(request.zoneId());
        return ResponseEntity.ok(ApiResponse.ok("Utilisateur mis à jour", UserResponse.from(userRepository.save(user))));
    }

    @Operation(summary = "Supprimer un utilisateur d'une IMF")
    @Transactional
    @DeleteMapping("/imf/{imfUid}/users/{userUid}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable UUID imfUid,
            @PathVariable UUID userUid) {

        Long imfId = resolveImfId(imfUid);
        User user = resolveUser(userUid, imfId);
        userRepository.delete(user);
        return ResponseEntity.ok(ApiResponse.ok("Utilisateur supprimé", null));
    }

    @Operation(summary = "Suspendre un utilisateur d'une IMF")
    @Transactional
    @PatchMapping("/imf/{imfUid}/users/{userUid}/suspend")
    public ResponseEntity<ApiResponse<UserResponse>> suspendUser(
            @PathVariable UUID imfUid,
            @PathVariable UUID userUid) {

        Long imfId = resolveImfId(imfUid);
        User user = resolveUser(userUid, imfId);
        user.setActif(false);
        return ResponseEntity.ok(ApiResponse.ok("Utilisateur suspendu", UserResponse.from(userRepository.save(user))));
    }

    @Operation(summary = "Réactiver un utilisateur suspendu")
    @Transactional
    @PatchMapping("/imf/{imfUid}/users/{userUid}/reactivate")
    public ResponseEntity<ApiResponse<UserResponse>> reactivateUser(
            @PathVariable UUID imfUid,
            @PathVariable UUID userUid) {

        Long imfId = resolveImfId(imfUid);
        User user = resolveUser(userUid, imfId);
        user.setActif(true);
        return ResponseEntity.ok(ApiResponse.ok("Utilisateur réactivé", UserResponse.from(userRepository.save(user))));
    }

    @Operation(summary = "Réleguer un utilisateur vers un autre de la même IMF")
    @Transactional
    @PostMapping("/imf/{imfUid}/users/{fromUserUid}/delegate")
    public ResponseEntity<ApiResponse<UserResponse>> delegateUser(
            @PathVariable UUID imfUid,
            @PathVariable UUID fromUserUid,
            @Valid @RequestBody DelegateUserRequest request) {

        Long imfId = resolveImfId(imfUid);
        User from = resolveUser(fromUserUid, imfId);
        User to   = resolveUser(request.toUserUid(), imfId);

        // Transfert de rôle : le destinataire prend le rôle de la source
        to.setRole(from.getRole());
        userRepository.save(to);

        // Suspension de l'utilisateur source
        from.setActif(false);
        return ResponseEntity.ok(ApiResponse.ok(
                "Relégation effectuée — " + from.getUsername() + " suspendu, " + to.getUsername() + " prend le rôle " + from.getRole(),
                UserResponse.from(userRepository.save(from))
        ));
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Long resolveImfId(UUID imfUid) {
        Imf imf = imfRepository.findByUid(imfUid)
                .orElseThrow(() -> new ResourceNotFoundException("IMF", imfUid));
        return imf.getId();
    }

    private User resolveUser(UUID userUid, Long imfId) {
        User user = userRepository.findByUid(userUid)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userUid));
        if (!imfId.equals(user.getImf() != null ? user.getImf().getId() : null)) {
            throw new BusinessException("Cet utilisateur n'appartient pas à l'IMF spécifiée.");
        }
        return user;
    }
}
