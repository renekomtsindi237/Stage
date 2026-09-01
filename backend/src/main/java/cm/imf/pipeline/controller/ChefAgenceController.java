package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.ChefAgenceDashboardResponse;
import cm.imf.pipeline.dto.response.ChefAgenceEquipePerformanceResponse;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final JdbcTemplate            jdbc;

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

    @Operation(summary = "Performances de l'équipe (collectes, dossiers) avec évolution vs période précédente")
    @GetMapping("/equipe/performances")
    public ResponseEntity<ApiResponse<ChefAgenceEquipePerformanceResponse>> performancesEquipe(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "30") int jours) {

        int period = jours <= 7 ? 7 : jours <= 30 ? 30 : 90;
        Long imfId = user.getImf().getId();
        LocalDate today = LocalDate.now();
        LocalDate fin = today.plusDays(1);
        LocalDate debut = today.minusDays(period);
        LocalDate debutPrec = debut.minusDays(period);

        List<Role> roles = List.of(
                Role.AGENT, Role.AGENT_CREDIT, Role.CAISSIER,
                Role.AGENT_SAISIE, Role.ANALYSTE_ENGAGEMENTS);
        List<User> membres = userRepo.findByImfIdAndRoleIn(imfId, roles,
                PageRequest.of(0, 200, Sort.by("username"))).getContent();
        if (user.getZoneId() != null && !user.getZoneId().isBlank()) {
            String zone = user.getZoneId();
            membres = membres.stream()
                    .filter(u -> zone.equals(u.getZoneId()))
                    .toList();
        }

        Map<Long, long[]> collectesAct = loadCollectes(imfId, debut, fin);
        Map<Long, long[]> collectesPrec = loadCollectes(imfId, debutPrec, debut);
        Map<Long, long[]> dossiersAct = loadDossiers(imfId, debut, fin);
        Map<Long, long[]> dossiersPrec = loadDossiers(imfId, debutPrec, debut);

        List<ChefAgenceEquipePerformanceResponse.MembrePerformance> rows = new ArrayList<>();
        for (User m : membres) {
            long[] cAct = collectesAct.getOrDefault(m.getId(), new long[]{0, 0, 0});
            long[] cPrec = collectesPrec.getOrDefault(m.getId(), new long[]{0, 0, 0});
            long[] dAct = dossiersAct.getOrDefault(m.getId(), new long[]{0, 0, 0});
            long[] dPrec = dossiersPrec.getOrDefault(m.getId(), new long[]{0, 0, 0});
            double evo = (cAct[1] != 0 || cPrec[1] != 0)
                    ? evolutionPct(cAct[1], cPrec[1])
                    : evolutionPct(dAct[1], dPrec[1]);
            String tendance = evo > 5 ? "HAUSSE" : evo < -5 ? "BAISSE" : "STABLE";
            long soumis = dAct[0];
            long valides = dAct[1];
            long rejetes = dAct[2];
            double taux = soumis > 0 ? Math.round(valides * 10_000.0 / soumis) / 100.0 : 0;
            rows.add(new ChefAgenceEquipePerformanceResponse.MembrePerformance(
                    m.getUid() != null ? m.getUid().toString() : null,
                    m.getUsername(),
                    m.getRole() != null ? m.getRole().name() : "",
                    m.getZoneId(),
                    m.isActif(),
                    cAct[0],
                    BigDecimal.valueOf(cAct[1]),
                    cPrec[0],
                    BigDecimal.valueOf(cPrec[1]),
                    evo,
                    tendance,
                    soumis,
                    valides,
                    rejetes,
                    taux,
                    cAct[2]
            ));
        }
        rows.sort((a, b) -> {
            int cmp = b.collectesMontant().compareTo(a.collectesMontant());
            if (cmp != 0) return cmp;
            return Long.compare(b.dossiersValides(), a.dossiersValides());
        });
        return ResponseEntity.ok(ApiResponse.ok(
                new ChefAgenceEquipePerformanceResponse(period, debut, today, rows)));
    }

    /** [count, montantFcfa, clientsDistincts] */
    private Map<Long, long[]> loadCollectes(Long imfId, LocalDate from, LocalDate to) {
        Map<Long, long[]> acc = new HashMap<>();
        addCollectes(acc, """
                SELECT agent_id, COUNT(*) nb, COALESCE(SUM(montant_collecte),0) tot,
                       COUNT(DISTINCT client_id_externe) clients
                FROM app.collectes_epargne
                WHERE imf_id = ? AND date_collecte >= ? AND date_collecte < ?
                  AND statut IN ('CONFIRMEE','SOUMISE')
                GROUP BY agent_id
                """, imfId, from, to);
        addCollectes(acc, """
                SELECT agent_id, COUNT(*) nb, COALESCE(SUM(montant_collecte),0) tot,
                       COUNT(DISTINCT client_id) clients
                FROM app.collectes_terrain
                WHERE imf_id = ? AND date_collecte >= ? AND date_collecte < ?
                  AND statut IN ('CONFIRMEE','SOUMISE')
                GROUP BY agent_id
                """, imfId, from, to);
        return acc;
    }

    private void addCollectes(Map<Long, long[]> acc, String sql, Long imfId, LocalDate from, LocalDate to) {
        try {
            jdbc.query(sql, rs -> {
                long id = rs.getLong("agent_id");
                long[] cur = acc.getOrDefault(id, new long[]{0, 0, 0});
                cur[0] += rs.getLong("nb");
                cur[1] += rs.getBigDecimal("tot") != null ? rs.getBigDecimal("tot").longValue() : 0L;
                cur[2] += rs.getLong("clients");
                acc.put(id, cur);
            }, imfId, from, to);
        } catch (Exception ignored) {
            /* table ou colonnes absentes selon l'environnement */
        }
    }

    /** [soumis, valides, rejetes] */
    private Map<Long, long[]> loadDossiers(Long imfId, LocalDate from, LocalDate to) {
        Map<Long, long[]> acc = new HashMap<>();
        try {
            jdbc.query("""
                    SELECT agent_credit_id,
                           COUNT(*) soumis,
                           SUM(CASE WHEN statut IN ('VALIDE','APPROUVE','DEBLOQUE') THEN 1 ELSE 0 END) valides,
                           SUM(CASE WHEN statut = 'REJETE' THEN 1 ELSE 0 END) rejetes
                    FROM app.dossiers_credit
                    WHERE imf_id = ?
                      AND COALESCE(date_soumission, created_at) >= ?
                      AND COALESCE(date_soumission, created_at) <  ?
                    GROUP BY agent_credit_id
                    """, rs -> {
                acc.put(rs.getLong("agent_credit_id"), new long[]{
                        rs.getLong("soumis"),
                        rs.getLong("valides"),
                        rs.getLong("rejetes")
                });
            }, imfId, from.atStartOfDay(), to.atStartOfDay());
        } catch (Exception ignored) {
        }
        return acc;
    }

    private static double evolutionPct(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return Math.round((current - previous) * 10_000.0 / previous) / 100.0;
    }
}
