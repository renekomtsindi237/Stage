package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.PositionRequest;
import cm.imf.pipeline.dto.response.AgentPositionResponse;
import cm.imf.pipeline.dto.response.AgentResponse;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.exception.ResourceNotFoundException;
import cm.imf.pipeline.repository.AgenceRepository;
import cm.imf.pipeline.repository.UserRepository;
import cm.imf.pipeline.security.TenantContext;
import cm.imf.pipeline.service.IAgentService;
import cm.imf.pipeline.service.IPositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/agents")
@RequiredArgsConstructor
@Tag(name = "Agents", description = "Consultation et géolocalisation des agents terrain")
public class AgentController {

    private final IAgentService     agentService;
    private final IPositionService  positionService;
    private final UserRepository    userRepository;
    private final AgenceRepository  agenceRepository;

    // ── Consultation ──────────────────────────────────────────────────────────

    @Operation(summary = "Liste paginée de tous les agents de l'IMF")
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> listAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<AgentResponse> data  = agentService.listAll(page, size);
        long                total = agentService.count();
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "content", data,
                "total",   total,
                "page",    page,
                "size",    size
        )));
    }

    @Operation(summary = "Agents d'une agence")
    @GetMapping("/agence/{idAgence}")
    public ResponseEntity<ApiResponse<List<AgentResponse>>> listByAgence(
            @PathVariable String idAgence) {
        return ResponseEntity.ok(ApiResponse.ok(agentService.listByAgence(idAgence)));
    }

    @Operation(summary = "Détail d'un agent")
    @GetMapping("/{idAgent}")
    public ResponseEntity<ApiResponse<AgentResponse>> getById(@PathVariable String idAgent) {
        return ResponseEntity.ok(ApiResponse.ok(agentService.getById(idAgent)));
    }

    @Operation(summary = "Recherche autocomplete d'agents par nom")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<AgentResponse>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(agentService.search(q, limit)));
    }

    // ── Géolocalisation — Agent (moi-même) ───────────────────────────────────

    /**
     * L'agent terrain envoie sa position GPS depuis l'application Flutter.
     * Appelé périodiquement (ex: toutes les 5 minutes en arrière-plan)
     * et systématiquement lors de chaque soumission de collecte.
     *
     * Le backend :
     *  1. Met à jour la dernière position dans app.utilisateurs
     *  2. Insère un point dans app.positions_agents (historique)
     *  3. Pousse un événement SSE AGENT_POSITION_UPDATED aux superviseurs
     */
    @Operation(summary = "Mettre à jour ma position GPS (agent terrain)")
    @PutMapping("/me/position")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<ApiResponse<AgentPositionResponse>> mettreAJourMaPosition(
            @Valid @RequestBody PositionRequest request) {

        User me = TenantContext.currentUser();
        AgentPositionResponse position = positionService.mettreAJourPosition(
                me.getId(), me.getImf().getId(), request);

        return ResponseEntity.ok(ApiResponse.ok(position));
    }

    /**
     * L'agent désactive le partage de sa position (droit d'opposition RGPD).
     * La dernière position est conservée en base mais marquée inactive.
     * Les superviseurs ne verront plus cet agent sur la carte.
     */
    @Operation(summary = "Désactiver le partage de ma position (RGPD)")
    @DeleteMapping("/me/position")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<ApiResponse<Void>> desactiverMaPosition() {
        User me = TenantContext.currentUser();
        positionService.desactiverPartage(me.getId(), me.getImf().getId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Géolocalisation — Supervision (responsable / directeur) ──────────────

    /**
     * Carte temps réel des agents terrain actifs.
     * Retourne uniquement les agents ayant partagé leur position dans les 15 dernières minutes.
     *
     * Usage : tableau de bord Angular (carte Leaflet/OpenStreetMap)
     *         rafraîchi par SSE AGENT_POSITION_UPDATED.
     */
    @Operation(summary = "Positions actuelles de tous les agents actifs (carte)")
    @GetMapping("/positions")
    @PreAuthorize("hasAnyRole('RESPONSABLE_RECOUVREMENT', 'DIRECTEUR', 'DSI', 'ANALYSTE', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<AgentPositionResponse>>> listerPositionsActives(
            @Parameter(description = "Filtrer par agence (optionnel, uid public)")
            @RequestParam(required = false) UUID agenceUid) {

        Long imfId = TenantContext.currentImfId();
        Long agenceId = agenceUid == null ? null
                : agenceRepository.findByUid(agenceUid).map(a -> a.getId()).orElse(null);
        List<AgentPositionResponse> positions =
                positionService.listerPositionsActives(imfId, agenceId);

        return ResponseEntity.ok(ApiResponse.ok(positions));
    }

    /**
     * Carte complète : retourne la DERNIÈRE position connue de tous les agents
     * qui ont au moins une coordonnée GPS (y compris partage désactivé).
     * Permet au Directeur de voir l'ensemble de la couverture terrain même
     * sans activité GPS en temps réel.
     */
    @Operation(summary = "Dernière position connue de tous les agents (carte complète)")
    @GetMapping("/positions/toutes")
    @PreAuthorize("hasAnyRole('RESPONSABLE_RECOUVREMENT', 'DIRECTEUR', 'DSI', 'ANALYSTE', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<AgentPositionResponse>>> listerDernieresPositions() {
        Long imfId = TenantContext.currentImfId();
        return ResponseEntity.ok(ApiResponse.ok(
                positionService.listerDernieresPositions(imfId)));
    }

    /**
     * Trajet journalier d'un agent (historique GPS).
     * Retourne jusqu'à 500 points de passage pour reconstruire la route
     * sur une carte et vérifier la couverture terrain.
     *
     * Accès restreint au RESPONSABLE_RECOUVREMENT et au DIRECTEUR de l'IMF de l'agent.
     */
    @Operation(summary = "Historique GPS d'un agent (trajet journalier)")
    @GetMapping("/{agentUid}/positions/historique")
    @PreAuthorize("hasAnyRole('RESPONSABLE_RECOUVREMENT', 'DIRECTEUR', 'DSI', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<AgentPositionResponse>>> historiquePositions(
            @PathVariable UUID agentUid,
            @Parameter(description = "Date du trajet (défaut = aujourd'hui, format YYYY-MM-DD)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        Long imfId = TenantContext.currentImfId();
        User agent = userRepository.findByUid(agentUid)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", agentUid));
        List<AgentPositionResponse> historique =
                positionService.historiqueJournalier(agent.getId(), imfId, date);

        return ResponseEntity.ok(ApiResponse.ok(historique));
    }
}
