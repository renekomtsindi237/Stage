package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.DemandeRgpdRequest;
import cm.imf.pipeline.dto.request.TraiterDemandeRgpdRequest;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.DemandeRgpdResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.entity.*;
import cm.imf.pipeline.exception.BusinessException;
import cm.imf.pipeline.exception.ResourceNotFoundException;
import cm.imf.pipeline.repository.*;
import cm.imf.pipeline.security.Auditable;
import cm.imf.pipeline.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints RGPD — Loi n° 2024/017 du 23 décembre 2024 (Cameroun).
 *
 * Droits exposés :
 *   POST  /api/mes-donnees/demande           — soumettre une demande (art. 37-43)
 *   GET   /api/mes-donnees/demandes          — suivi de ses propres demandes
 *   GET   /api/mes-donnees                   — export de ses propres données (art. 37/43)
 *   GET   /api/admin/rgpd/demandes           — liste toutes les demandes (DSI)
 *   PUT   /api/admin/rgpd/demandes/{id}      — traiter une demande (DSI)
 *   GET   /api/admin/rgpd/demandes/en-retard — SLA dépassé (DSI)
 *   GET   /api/admin/rgpd/consentements/{type}/{sujetId} — état des consentements (DSI)
 *   PUT   /api/admin/rgpd/consentements/{type}/{sujetId}/{finalite} — révoquer (DSI)
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "RGPD", description = "Droits des personnes — Loi 2024/017 Cameroun art. 37-43")
public class RgpdController {

    private final DemandeRgpdRepository    demandeRepository;
    private final ConsentementRepository   consentementRepository;
    private final UserRepository           userRepository;

    // ── Espace personnel : l'utilisateur connecté gère ses propres droits ─────

    @Operation(summary = "Soumettre une demande d'exercice de droit (art. 37-43)",
               description = "Délai légal de réponse : 30 jours (art. 41).")
    @PostMapping("/mes-donnees/demande")
    @PreAuthorize("isAuthenticated()")
    @Auditable(action = AuditTrail.ACTION_DEMANDE_RGPD, entiteType = AuditTrail.ENTITE_AUTH,
               motifExpression = "#req.typeDroit")
    public ResponseEntity<ApiResponse<DemandeRgpdResponse>> soumettreDemande(
            @Valid @RequestBody DemandeRgpdRequest req,
            HttpServletRequest httpReq) {

        User moi = TenantContext.currentUser();

        DemandeRgpd demande = DemandeRgpd.builder()
                .imfId(moi.getImf() != null ? moi.getImf().getId() : null)
                .demandeurId(moi.getId())
                .demandeurUsername(moi.getUsername())
                .demandeurEmail(moi.getEmail())
                .typeDroit(req.typeDroit())
                .perimetre(req.perimetre())
                .finaliteConcernee(req.finaliteConcernee())
                .ipSoumission(extraireIp(httpReq))
                .build();

        demande = demandeRepository.save(demande);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toResponse(demande)));
    }

    @Operation(summary = "Suivi de mes demandes RGPD en cours")
    @GetMapping("/mes-donnees/demandes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<DemandeRgpdResponse>>> mesDemandes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        User moi = TenantContext.currentUser();
        PageRequest pageable = PageRequest.of(page, size, Sort.by("dateSoumission").descending());

        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.from(
                        demandeRepository.findByDemandeurIdOrderByDateSoumissionDesc(moi.getId(), pageable),
                        this::toResponse)));
    }

    @Operation(summary = "Accéder à mes données personnelles (art. 37 / portabilité art. 43)",
               description = "Retourne un résumé structuré des données de l'utilisateur courant.")
    @GetMapping("/mes-donnees")
    @PreAuthorize("isAuthenticated()")
    @Auditable(action = AuditTrail.ACTION_EXPORT, entiteType = AuditTrail.ENTITE_UTILISATEUR,
               entiteIdExpression = "#currentUserId")
    public ResponseEntity<ApiResponse<Map<String, Object>>> mesDonnees() {

        User moi = TenantContext.currentUser();

        // Données de profil exportées (sans données sensibles d'autres utilisateurs)
        Map<String, Object> donnees = Map.of(
                "username",          moi.getUsername(),
                "email",             moi.getEmail() != null ? moi.getEmail() : "",
                "role",              moi.getRole().name(),
                "prefLangue",        moi.getPrefLangue(),
                "prefTheme",         moi.getPrefTheme(),
                "notificationsActives", moi.isNotificationsActives(),
                "imf",               moi.getImf() != null ? moi.getImf().getNom() : "",
                "createdAt",         moi.getCreatedAt() != null ? moi.getCreatedAt().toString() : "",
                "consentements",     consentementRepository
                        .findByImfIdAndSujetTypeAndSujetId(
                                moi.getImf() != null ? moi.getImf().getId() : -1L,
                                "AGENT", moi.getId())
                        .stream()
                        .map(c -> Map.of(
                                "finalite",          c.getFinalite(),
                                "accorde",           c.isAccorde(),
                                "dateConsentement",  c.getDateConsentement().toString()
                        ))
                        .toList()
        );

        return ResponseEntity.ok(ApiResponse.ok(donnees));
    }

    // ── Administration DSI : traitement des demandes ──────────────────────────

    @Operation(summary = "Liste toutes les demandes RGPD de l'IMF (DSI)")
    @GetMapping("/admin/rgpd/demandes")
    @PreAuthorize("hasAnyRole('DSI', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<DemandeRgpdResponse>>> listerDemandes(
            @RequestParam(required = false) String statut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Long imfId = TenantContext.currentImfId();
        PageRequest pageable = PageRequest.of(page, size, Sort.by("dateSoumission").descending());

        var springPage = statut != null
                ? demandeRepository.findByImfIdAndStatutOrderByDateSoumissionDesc(imfId, statut, pageable)
                : demandeRepository.findByImfIdOrderByDateSoumissionDesc(imfId, pageable);

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(springPage, this::toResponse)));
    }

    @Operation(summary = "Traiter une demande RGPD (DSI)",
               description = "Met à jour le statut et enregistre la réponse au demandeur.")
    @PutMapping("/admin/rgpd/demandes/{uid}")
    @PreAuthorize("hasAnyRole('DSI', 'SUPER_ADMIN')")
    @Auditable(action = AuditTrail.ACTION_MODIFICATION, entiteType = AuditTrail.ENTITE_CONSENTEMENT,
               entiteIdExpression = "#uid.toString()", motifExpression = "#req.statut")
    public ResponseEntity<ApiResponse<DemandeRgpdResponse>> traiterDemande(
            @PathVariable UUID uid,
            @Valid @RequestBody TraiterDemandeRgpdRequest req) {

        Long imfId = TenantContext.currentImfId();
        User moi   = TenantContext.currentUser();

        DemandeRgpd demande = demandeRepository.findByUid(uid)
                .filter(d -> d.getImfId().equals(imfId))
                .orElseThrow(() -> new ResourceNotFoundException("DemandeRgpd", uid));

        if ("TRAITEE".equals(demande.getStatut()) || "REFUSEE".equals(demande.getStatut())) {
            throw new BusinessException("Cette demande est déjà clôturée");
        }

        demande.setStatut(req.statut());
        demande.setReponse(req.reponse());
        demande.setMotifRefus(req.motifRefus());
        demande.setExportUrl(req.exportUrl());
        demande.setTraiteParId(moi.getId());
        demande.setTraiteParUsername(moi.getUsername());
        demande.setDateTraitement(OffsetDateTime.now());

        if (req.exportUrl() != null) {
            // Lien d'export valide 7 jours
            demande.setExportExpireAt(OffsetDateTime.now().plusDays(7));
        }

        demande = demandeRepository.save(demande);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(demande)));
    }

    @Operation(summary = "Demandes RGPD en retard (SLA 30j dépassé — art. 41)")
    @GetMapping("/admin/rgpd/demandes/en-retard")
    @PreAuthorize("hasAnyRole('DSI', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<DemandeRgpdResponse>>> demandesEnRetard() {

        Long imfId = TenantContext.currentImfId();
        List<DemandeRgpdResponse> enRetard = demandeRepository
                .findEnRetard(imfId, OffsetDateTime.now())
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(enRetard));
    }

    // ── Consentements ─────────────────────────────────────────────────────────

    @Operation(summary = "État des consentements d'un agent ou client (DSI)")
    @GetMapping("/admin/rgpd/consentements/{sujetType}/{sujetUid}")
    @PreAuthorize("hasAnyRole('DSI', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listerConsentements(
            @PathVariable String sujetType,
            @PathVariable UUID sujetUid) {

        Long imfId = TenantContext.currentImfId();
        Long sujetId = resolveSujetId(sujetType, sujetUid);
        if (sujetId == null) return ResponseEntity.ok(ApiResponse.ok(List.of()));
        List<Map<String, Object>> consentements = consentementRepository
                .findByImfIdAndSujetTypeAndSujetId(imfId, sujetType.toUpperCase(), sujetId)
                .stream()
                .map(c -> Map.<String, Object>of(
                        "uid",               c.getUid() != null ? c.getUid().toString() : null,
                        "finalite",          c.getFinalite(),
                        "accorde",           c.isAccorde(),
                        "dateConsentement",  c.getDateConsentement().toString(),
                        "dateRetrait",       c.getDateRetrait() != null ? c.getDateRetrait().toString() : null,
                        "canalCollecte",     c.getCanalCollecte(),
                        "versionPolitique",  c.getVersionPolitique()
                ))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(consentements));
    }

    /**
     * Le DSI accorde le consentement de géolocalisation pour un agent de terrain.
     * Sans ce consentement, PositionServiceImpl refuse tout enregistrement GPS (art. 9/50).
     * Le DSI représente l'employeur (IMF) — c'est lui qui autorise le suivi professionnel.
     */
    @Operation(summary = "Accorder le consentement GPS à un agent (DSI — art. 9/50)",
               description = "Sans ce consentement, l'agent ne peut pas envoyer sa position.")
    @PutMapping("/admin/rgpd/consentements/agents/{agentUid}/GEOLOCALISATION")
    @PreAuthorize("hasAnyRole('DSI', 'SUPER_ADMIN')")
    @Auditable(action = AuditTrail.ACTION_CONSENTEMENT, entiteType = AuditTrail.ENTITE_UTILISATEUR,
               entiteIdExpression = "#agentUid.toString()")
    public ResponseEntity<ApiResponse<Void>> accorderConsentementGps(
            @PathVariable UUID agentUid,
            @RequestParam(defaultValue = "Autorisation géolocalisation professionnelle accordée par DSI") String motif,
            HttpServletRequest httpReq) {

        Long imfId = TenantContext.currentImfId();
        User moi   = TenantContext.currentUser();
        Long agentId = userRepository.findByUid(agentUid)
                .map(u -> u.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Agent", agentUid));

        Consentement consentement = consentementRepository
                .findByImfIdAndSujetTypeAndSujetIdAndFinalite(
                        imfId, "AGENT", agentId, Consentement.FINALITE_GEOLOCALISATION)
                .orElseGet(() -> Consentement.builder()
                        .imfId(imfId)
                        .sujetType("AGENT")
                        .sujetId(agentId)
                        .finalite(Consentement.FINALITE_GEOLOCALISATION)
                        .canalCollecte("APPLICATION")
                        .collecteParId(moi.getId())
                        .ipCollecte(extraireIp(httpReq))
                        .build());

        consentement.setAccorde(true);
        consentement.setDateConsentement(OffsetDateTime.now());
        consentement.setDateRetrait(null);
        consentement.setNotes(motif);
        consentementRepository.save(consentement);

        return ResponseEntity.ok(ApiResponse.ok(
                "Consentement de géolocalisation accordé pour agent " + agentUid));
    }

    @Operation(summary = "Révoquer un consentement spécifique (art. 40 — droit d'opposition)")
    @DeleteMapping("/admin/rgpd/consentements/{sujetType}/{sujetUid}/{finalite}")
    @PreAuthorize("hasAnyRole('DSI', 'SUPER_ADMIN')")
    @Auditable(action = AuditTrail.ACTION_CONSENTEMENT, entiteType = AuditTrail.ENTITE_CONSENTEMENT,
               entiteIdExpression = "#sujetUid.toString() + '_' + #finalite")
    public ResponseEntity<ApiResponse<Void>> revoquerConsentement(
            @PathVariable String sujetType,
            @PathVariable UUID sujetUid,
            @PathVariable String finalite) {

        Long imfId = TenantContext.currentImfId();
        Long sujetId = resolveSujetId(sujetType, sujetUid);
        if (sujetId == null) throw new ResourceNotFoundException("Consentement",
                sujetType + "/" + sujetUid + "/" + finalite);

        Consentement consentement = consentementRepository
                .findByImfIdAndSujetTypeAndSujetIdAndFinalite(
                        imfId, sujetType.toUpperCase(), sujetId, finalite)
                .orElseThrow(() -> new ResourceNotFoundException("Consentement",
                        sujetType + "/" + sujetUid + "/" + finalite));

        consentement.setAccorde(false);
        consentement.setDateRetrait(OffsetDateTime.now());
        consentementRepository.save(consentement);

        return ResponseEntity.ok(ApiResponse.ok("Consentement révoqué pour finalité : " + finalite));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Exposé pour SpEL dans @Auditable. */
    public String currentUserId() {
        User u = TenantContext.currentUser();
        return u != null ? String.valueOf(u.getId()) : null;
    }

    private Long resolveSujetId(String sujetType, UUID sujetUid) {
        return switch (sujetType.toUpperCase()) {
            case "AGENT" -> userRepository.findByUid(sujetUid).map(u -> u.getId()).orElse(null);
            default      -> userRepository.findByUid(sujetUid).map(u -> u.getId()).orElse(null);
        };
    }

    private DemandeRgpdResponse toResponse(DemandeRgpd d) {
        long joursRestants = ChronoUnit.DAYS.between(OffsetDateTime.now(), d.getDateLimiteReponse());
        return new DemandeRgpdResponse(
                d.getUid() != null ? d.getUid().toString() : null,
                d.getDemandeurUsername(),
                d.getTypeDroit(),
                d.getPerimetre(),
                d.getFinaliteConcernee(),
                d.getStatut(),
                d.getDateSoumission(),
                d.getDateLimiteReponse(),
                d.getDateTraitement(),
                d.getTraiteParUsername(),
                d.getReponse(),
                joursRestants,
                d.getCreatedAt()
        );
    }

    private String extraireIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
