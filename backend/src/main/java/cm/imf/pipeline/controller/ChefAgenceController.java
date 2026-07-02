package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.ChefAgenceDashboardResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.dto.response.UserResponse;
import cm.imf.pipeline.entity.DossierCredit;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.enums.StatutEcheance;
import cm.imf.pipeline.repository.ClientInformelRepository;
import cm.imf.pipeline.repository.CollecteRepository;
import cm.imf.pipeline.repository.DossierCreditRepository;
import cm.imf.pipeline.repository.EcheanceAppRepository;
import cm.imf.pipeline.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/chef-agence")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CHEF_AGENCE')")
@Tag(name = "Chef d'Agence", description = "Tableau de bord et gestion d'agence pour le Chef d'Agence")
public class ChefAgenceController {

    private final DossierCreditRepository dossierRepo;
    private final UserRepository          userRepo;
    private final ClientInformelRepository clientRepo;
    private final CollecteRepository      collecteRepo;
    private final EcheanceAppRepository   echeanceRepo;

    @Operation(summary = "Tableau de bord du Chef d'Agence")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<ChefAgenceDashboardResponse>> dashboard(
            @AuthenticationPrincipal User user) {

        Long imfId = user.getImf().getId();

        // ── KPIs ──────────────────────────────────────────────────────────────
        long agentsCount = userRepo.countByImfIdAndRoleIn(imfId,
                List.of(Role.AGENT, Role.AGENT_CREDIT));

        long clientsCount = clientRepo.countByImfId(imfId);

        long collectesJour = collecteRepo.countByImfIdAndDateCollecte(imfId, LocalDate.now());

        // PAR 30 — ratio échéances EN_RETARD / actives (EN_ATTENTE + PARTIELLE + EN_RETARD)
        long retard    = echeanceRepo.countByImfIdAndStatut(imfId, StatutEcheance.EN_RETARD);
        long enAttente = echeanceRepo.countByImfIdAndStatut(imfId, StatutEcheance.EN_ATTENTE);
        long partielle = echeanceRepo.countByImfIdAndStatut(imfId, StatutEcheance.PARTIELLE);
        long totalActif = retard + enAttente + partielle;
        double par30 = totalActif > 0
                ? Math.round((double) retard / totalActif * 10_000.0) / 100.0
                : 0.0;

        // ── Dossiers en attente (EN_COMITE) ──────────────────────────────────
        List<DossierCredit> pending = dossierRepo
                .findByImfIdAndStatutOrderByDateSoumissionAsc(imfId, "EN_COMITE");

        long dossiersEnAttente = pending.size();

        long dossiersValidesMois = dossierRepo.countByImfIdAndStatut(imfId, "VALIDE")
                + dossierRepo.countByImfIdAndStatut(imfId, "APPROUVE");

        // Enrichir avec le nom de l'agent
        Set<Long> agentIds = pending.stream()
                .map(DossierCredit::getAgentCreditId)
                .collect(Collectors.toSet());
        Map<Long, String> agentNames = userRepo.findAllById(agentIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        List<ChefAgenceDashboardResponse.DossierPendant> dossiersDto = pending.stream()
                .map(d -> new ChefAgenceDashboardResponse.DossierPendant(
                        d.getUid().toString(),
                        d.getClientNom(),
                        d.getClientId(),
                        d.getMontantDemande(),
                        d.getDureeMois(),
                        d.getSecteurActivite(),
                        d.getObjetFinancement(),
                        agentNames.getOrDefault(d.getAgentCreditId(), "—"),
                        d.getDateSoumission(),
                        d.getStatut(),
                        d.getNoteAnalyse()
                ))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(new ChefAgenceDashboardResponse(
                agentsCount, clientsCount, collectesJour, par30,
                dossiersEnAttente, dossiersValidesMois, dossiersDto
        )));
    }

    @Operation(summary = "Liste des membres de l'équipe (agents, chargés de clientèle, caissiers…)")
    @GetMapping("/equipe")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> equipe(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Long imfId = user.getImf().getId();
        List<Role> roles = List.of(
                Role.AGENT, Role.AGENT_CREDIT, Role.CHEF_AGENCE,
                Role.CAISSIER, Role.AGENT_SAISIE, Role.ANALYSTE_ENGAGEMENTS);

        Page<User> result = userRepo.findByImfIdAndRoleIn(imfId, roles,
                PageRequest.of(page, size, Sort.by("username")));

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result, UserResponse::from)));
    }
}
