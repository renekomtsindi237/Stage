package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.SseEventDto;
import cm.imf.pipeline.entity.AuditTrail;
import cm.imf.pipeline.entity.TicketSupport;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.repository.TicketSupportRepository;
import cm.imf.pipeline.security.Auditable;
import cm.imf.pipeline.security.TenantContext;
import cm.imf.pipeline.service.INotificationService;
import cm.imf.pipeline.sse.SseEmitterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Gestion des tickets de support — création par tout utilisateur connecté,
 * consultation et mise à jour par l'équipe SUPPORT.
 */
@Slf4j
@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
@Tag(name = "Tickets Support", description = "Création et suivi des tickets de support")
public class TicketSupportController {

    private final TicketSupportRepository ticketRepo;
    private final INotificationService    notificationService;
    private final SseEmitterRegistry      sseRegistry;

    // ── DTO inline ────────────────────────────────────────────────────────────

    record CreerTicketRequest(
            String titre,
            String description,
            String categorie,
            String priorite
    ) {}

    // ── POST /api/v1/tickets ──────────────────────────────────────────────────

    @Operation(summary = "Créer un nouveau ticket de support")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Auditable(action = AuditTrail.ACTION_CREATION, entiteType = "TICKET_SUPPORT",
               captureResult = true)
    public ResponseEntity<ApiResponse<TicketSupport>> creer(
            @RequestBody CreerTicketRequest req) {

        User moi   = TenantContext.currentUser();
        Long imfId = TenantContext.currentImfId();

        TicketSupport ticket = TicketSupport.builder()
                .imfId(imfId)
                .auteurId(moi.getId())
                .auteurUsername(moi.getUsername())
                .auteurRole(moi.getRole().name())
                .titre(req.titre())
                .description(req.description())
                .categorie(req.categorie() != null ? req.categorie() : "AUTRE")
                .priorite(req.priorite()  != null ? req.priorite()  : "NORMALE")
                .statut("OUVERT")
                .build();

        ticketRepo.save(ticket);

        String msg = "Nouveau ticket de " + moi.getUsername() + " [" + ticket.getCategorie() + "] : " + ticket.getTitre();
        log.info("Ticket créé [uid={}] par {} — catégorie={} priorité={}",
                ticket.getUid(), moi.getUsername(), ticket.getCategorie(), ticket.getPriorite());

        // Notifier l'équipe SUPPORT via SSE
        sseRegistry.broadcastToRole("SUPPORT", new SseEventDto(
                "NOUVEAU_TICKET",
                "SUPPORT",
                msg,
                Map.of(
                        "ticketId",   ticket.getId(),
                        "uid",        ticket.getUid().toString(),
                        "categorie",  ticket.getCategorie(),
                        "priorite",   ticket.getPriorite(),
                        "auteur",     moi.getUsername()
                ),
                OffsetDateTime.now()
        ));

        // Push FCM vers tous les SUPPORT
        notificationService.sendPushToRole(Role.SUPPORT, "Nouveau ticket : " + ticket.getTitre(), msg);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(ticket));
    }

    // ── GET /api/v1/tickets/mes-tickets ───────────────────────────────────────

    @Operation(summary = "Mes tickets (utilisateur connecté)")
    @GetMapping("/mes-tickets")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<TicketSupport>>> mesTickets(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        User moi = TenantContext.currentUser();
        Page<TicketSupport> tickets = ticketRepo.findByAuteurIdOrderByCreatedAtDesc(
                moi.getId(), PageRequest.of(page, size));

        log.debug("Mes tickets pour {} : {} résultats", moi.getUsername(), tickets.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(tickets));
    }
}
