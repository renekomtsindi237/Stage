package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Proxy IA — Groq API (compatible OpenAI) avec function calling.
 *
 * Llama-3.3-70b-versatile peut appeler 7 outils backend pour répondre
 * aux questions sur les données réelles de l'IMF (PAR, MCRS, collectes,
 * dossiers, alertes, agents, tickets support).
 *
 * Flux :
 *   1. Tour 1 → Groq avec tools définis → si finish_reason = "tool_calls"
 *   2. Exécution SQL des outils demandés
 *   3. Tour 2 → Groq avec résultats → réponse finale en français
 *
 * Variable d'environnement requise : GROQ_API_KEY
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
public class AiChatController {

    private final JdbcTemplate jdbc;
    private final ObjectMapper  mapper;

    @Value("${imf.ai.api-key:}")
    private String apiKey;

    @Value("${imf.ai.base-url:https://api.groq.com/openai/v1}")
    private String baseUrl;

    @Value("${imf.ai.model:llama-3.3-70b-versatile}")
    private String model;

    @Value("${imf.ai.max-tokens:2048}")
    private int maxTokens;

    // ─── Système prompt ───────────────────────────────────────────────────────

    private static final String BASE_SYSTEM = """
        Tu es l'assistant IA de MicroRecouv, une plateforme de microfinance camerounaise conforme COBAC/BEAC/CEMAC.
        Tu aides directeurs, agents terrain, analystes et le support technique.

        RÈGLES STRICTES :
        1. Réponds TOUJOURS en français, concis et structuré.
        2. Utilise UNIQUEMENT les données du contexte et des outils. Ne les invente jamais.
        3. Si tu cites un chiffre, explique ce qu'il signifie pour l'utilisateur.
        4. Formule des recommandations concrètes et actionnables (pas de généralités).
        5. Utilise des listes à puces pour les réponses multi-points.
        6. Ne répète pas la question. Pas d'introduction du type "Bien sûr !".
        7. Si la question nécessite des données précises, utilise les outils disponibles.
        8. Longueur idéale : 4-8 lignes ou 3-6 puces.

        RÉFÉRENTIEL COBAC/BEAC :
        - Classes COBAC : A(0-29j/0%), B(30-89j/20%), C(90-179j/50%), D(180-359j/80%), E(≥360j/100%).
        - PAR30 > 5% = alerte | PAR30 > 10% = critique (normes CEMAC).
        - MCRS : [0, 0.30[ = Faible | [0.30, 0.55[ = Modéré | [0.55, 0.75[ = Élevé | [0.75, 1.00] = Critique.
        """;

    // ─── DTOs ─────────────────────────────────────────────────────────────────

    record ChatMessage(String role, String content) {}
    record ChatRequest(List<ChatMessage> messages) {}

    // ─── Endpoint ─────────────────────────────────────────────────────────────

    @PostMapping("/chat")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> chat(
            @RequestBody ChatRequest req,
            @AuthenticationPrincipal User user) {

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GROQ_API_KEY absent — assistant IA désactivé");
            return ok("L'assistant IA n'est pas configuré. Vérifiez que la variable GROQ_API_KEY est définie sur le serveur.");
        }

        Long imfId = TenantContext.currentImfId();
        try {
            String systemPrompt = BASE_SYSTEM + "\n" + buildContexteInitial(user, imfId);
            List<Map<String, Object>> messages = buildMessages(systemPrompt, req.messages());
            String answer = callGroq(messages, true, imfId);
            return ok(answer);
        } catch (Exception e) {
            log.error("Erreur appel IA : {}", e.getMessage());
            return ok("Désolé, je rencontre une difficulté technique. Réessayez dans quelques instants.");
        }
    }

    // ─── Appel Groq avec gestion du function calling ──────────────────────────

    @SuppressWarnings("unchecked")
    private String callGroq(List<Map<String, Object>> messages, boolean withTools, Long imfId) throws Exception {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model",      model);
        body.put("messages",   messages);
        body.put("max_tokens", maxTokens);
        body.put("temperature", 0.3);
        body.put("stream",     false);
        if (withTools) {
            body.put("tools",       buildTools());
            body.put("tool_choice", "auto");
        }

        var resp = new RestTemplate().exchange(
            baseUrl + "/chat/completions",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            Map.class
        );

        var choices = (List<Map<String, Object>>) resp.getBody().get("choices");
        var choice  = choices.get(0);
        var msg     = (Map<String, Object>) choice.get("message");
        var finish  = (String) choice.get("finish_reason");

        // Le modèle veut appeler un ou plusieurs outils
        if ("tool_calls".equals(finish)) {
            var toolCalls = (List<Map<String, Object>>) msg.get("tool_calls");
            log.info("IA tool_calls : {}", toolCalls.stream()
                .map(tc -> ((Map<String, Object>) tc.get("function")).get("name")).toList());

            messages.add(msg);  // message assistant avec les tool_calls

            for (var tc : toolCalls) {
                String toolId   = (String) tc.get("id");
                var    fn       = (Map<String, Object>) tc.get("function");
                String toolName = (String) fn.get("name");
                String argsJson = (String) fn.get("arguments");
                Map<String, Object> args = (argsJson != null && !argsJson.isBlank())
                    ? mapper.readValue(argsJson, Map.class) : Map.of();

                String result = executeTool(toolName, args, imfId);
                log.debug("Outil {} → {} chars", toolName, result.length());

                messages.add(Map.of(
                    "role",         "tool",
                    "tool_call_id", toolId,
                    "content",      result
                ));
            }
            // Tour 2 : réponse finale avec les résultats des outils
            return callGroq(messages, false, imfId);
        }

        return (String) msg.get("content");
    }

    // ─── Définition des 7 outils disponibles ─────────────────────────────────

    private List<Map<String, Object>> buildTools() {
        return List.of(
            tool("get_statistiques_imf",
                "Statistiques générales de l'IMF : clients scorés, MCRS moyen, PAR30/60/90, collectes du mois, dossiers recouvrement.",
                Map.of()),

            tool("get_clients_a_risque",
                "Liste des clients par niveau de risque MCRS avec leurs scores et alertes actives.",
                Map.of("niveau", Map.of(
                    "type",        "string",
                    "enum",        List.of("ELEVE", "CRITIQUE", "TOUS"),
                    "description", "Niveau de risque à filtrer. TOUS = ÉLEVÉ + CRITIQUE."
                ))),

            tool("get_collectes_recentes",
                "Résumé des collectes confirmées sur une période : nombre, montants, détail par agent.",
                Map.of("jours", Map.of(
                    "type",        "integer",
                    "description", "Nombre de jours en arrière (1=aujourd'hui, 7=semaine, 30=mois)."
                ))),

            tool("get_dossiers_recouvrement",
                "Dossiers de recouvrement avec statut, priorité, encours et prochaine action planifiée.",
                Map.of("statut", Map.of(
                    "type",        "string",
                    "enum",        List.of("EN_COURS", "TOUS"),
                    "description", "EN_COURS = actifs uniquement, TOUS = y compris clos."
                ))),

            tool("get_alertes_actives",
                "Alertes actives : alertes ML prédictives (MCRS critique, dérive) et alertes PAR opérationnelles.",
                Map.of()),

            tool("get_agents_performance",
                "Performances des agents terrain sur 30 jours : collectes, montants, clients actifs par agent.",
                Map.of()),

            tool("get_tickets_support",
                "Tickets de support ouverts ou en cours, triés par priorité. Réservé au rôle SUPPORT.",
                Map.of())
        );
    }

    private static Map<String, Object> tool(String name, String description, Map<String, Object> props) {
        var params = new LinkedHashMap<String, Object>();
        params.put("type",       "object");
        params.put("properties", props);
        if (!props.isEmpty()) params.put("required", new ArrayList<>(props.keySet()));
        return Map.of(
            "type",     "function",
            "function", Map.of("name", name, "description", description, "parameters", params)
        );
    }

    // ─── Exécution des outils ─────────────────────────────────────────────────

    private String executeTool(String name, Map<String, Object> args, Long imfId) {
        try {
            return switch (name) {
                case "get_statistiques_imf"      -> toolStatistiques(imfId);
                case "get_clients_a_risque"      -> toolClientsRisque(imfId, str(args, "niveau", "TOUS"));
                case "get_collectes_recentes"    -> toolCollectes(imfId, toInt(args.get("jours"), 7));
                case "get_dossiers_recouvrement" -> toolDossiers(imfId, str(args, "statut", "EN_COURS"));
                case "get_alertes_actives"       -> toolAlertes(imfId);
                case "get_agents_performance"    -> toolAgents(imfId);
                case "get_tickets_support"       -> toolTickets();
                default -> "Outil inconnu : " + name;
            };
        } catch (Exception e) {
            log.warn("Erreur outil {} : {}", name, e.getMessage());
            return "Données indisponibles pour l'outil " + name + ".";
        }
    }

    // ─── Implémentations ──────────────────────────────────────────────────────

    private String toolStatistiques(Long imfId) {
        if (imfId == null) return "Vue multi-IMF : utilisez get_tickets_support pour les données support.";
        var sb = new StringBuilder();
        try {
            var r = jdbc.queryForMap(
                "SELECT COUNT(*) nb, ROUND(CAST(AVG(score_mcrs) AS NUMERIC),3) avg_mcrs," +
                "  SUM(CASE WHEN niveau_risque='CRITIQUE' THEN 1 ELSE 0 END) crit," +
                "  SUM(CASE WHEN niveau_risque='ELEVE'    THEN 1 ELSE 0 END) elev," +
                "  SUM(CASE WHEN niveau_risque='MODERE'   THEN 1 ELSE 0 END) mod_," +
                "  SUM(CASE WHEN niveau_risque='FAIBLE'   THEN 1 ELSE 0 END) faib" +
                " FROM ml.client_scores WHERE imf_id=?", imfId);
            sb.append("Clients scorés : ").append(r.get("nb")).append("\n");
            sb.append("MCRS moyen : ").append(r.get("avg_mcrs")).append("\n");
            sb.append("Distribution : FAIBLE=").append(r.get("faib"))
              .append(" MODÉRÉ=").append(r.get("mod_"))
              .append(" ÉLEVÉ=").append(r.get("elev"))
              .append(" CRITIQUE=").append(r.get("crit")).append("\n");
        } catch (Exception ignored) {}
        try {
            var p = jdbc.queryForMap("SELECT * FROM app.v_par_par_imf WHERE imf_id=?", imfId);
            sb.append("PAR30=").append(fmt(p.get("taux_par30"))).append("%")
              .append(" | PAR60=").append(fmt(p.get("taux_par60"))).append("%")
              .append(" | PAR90=").append(fmt(p.get("taux_par90"))).append("%\n");
            sb.append("Encours total : ").append(fcfa(p.get("encours_total"))).append("\n");
        } catch (Exception ignored) {}
        try {
            var c = jdbc.queryForMap(
                "SELECT COUNT(*) nb, COALESCE(SUM(montant_collecte),0) tot" +
                " FROM app.collectes_epargne WHERE imf_id=? AND statut='CONFIRMEE'" +
                " AND date_collecte >= CURRENT_DATE-INTERVAL '30 days'", imfId);
            sb.append("Collectes 30j : ").append(c.get("nb")).append(" opérations — ").append(fcfa(c.get("tot"))).append("\n");
        } catch (Exception ignored) {}
        try {
            var d = jdbc.queryForMap(
                "SELECT COUNT(*) tot, SUM(CASE WHEN priorite_scoring>=4 THEN 1 ELSE 0 END) urg" +
                " FROM app.dossiers_recouvrement WHERE imf_id=? AND statut NOT IN ('CLOS','ABANDON')", imfId);
            sb.append("Dossiers recouvrement actifs : ").append(d.get("tot"))
              .append(" (urgents : ").append(d.get("urg")).append(")\n");
        } catch (Exception ignored) {}
        return sb.isEmpty() ? "Statistiques indisponibles." : sb.toString();
    }

    private String toolClientsRisque(Long imfId, String niveau) {
        if (imfId == null) return "Non disponible pour le rôle SUPPORT.";
        String cond = "TOUS".equals(niveau) ? "niveau_risque IN ('ELEVE','CRITIQUE')" : "niveau_risque='" + niveau + "'";
        try {
            var rows = jdbc.queryForList(
                "SELECT cs.client_id_externe, cs.niveau_risque," +
                "  ROUND(CAST(cs.score_mcrs AS NUMERIC),3) mcrs," +
                "  ROUND(CAST(cs.score_rps AS NUMERIC),3) rps," +
                "  ci.nom_complet" +
                " FROM ml.client_scores cs" +
                " LEFT JOIN app.clients_informels ci ON ci.client_id_externe=cs.client_id_externe AND ci.imf_id=cs.imf_id" +
                " WHERE cs.imf_id=? AND " + cond +
                " ORDER BY cs.score_mcrs DESC LIMIT 15", imfId);
            if (rows.isEmpty()) return "Aucun client à risque " + niveau + " actuellement.";
            var sb = new StringBuilder("Clients ").append(niveau).append(" (").append(rows.size()).append(") :\n");
            rows.forEach(r -> sb.append("• ").append(r.get("client_id_externe"))
                .append(" — ").append(r.getOrDefault("nom_complet", "?"))
                .append(" | MCRS=").append(r.get("mcrs"))
                .append(" | P(défaut)=").append(r.get("rps"))
                .append(" | ").append(r.get("niveau_risque")).append("\n"));
            return sb.toString();
        } catch (Exception e) { return "Données clients risque indisponibles."; }
    }

    private String toolCollectes(Long imfId, int jours) {
        if (imfId == null) return "Non disponible pour le rôle SUPPORT.";
        try {
            var rows = jdbc.queryForList(
                "SELECT u.prenom||' '||u.nom agent, COUNT(*) nb, COALESCE(SUM(ce.montant_collecte),0) tot" +
                " FROM app.collectes_epargne ce" +
                " JOIN app.utilisateurs u ON u.id=ce.agent_id" +
                " WHERE ce.imf_id=? AND ce.statut='CONFIRMEE'" +
                " AND ce.date_collecte >= CURRENT_DATE-(? ||' days')::INTERVAL" +
                " GROUP BY u.prenom,u.nom ORDER BY tot DESC LIMIT 10", imfId, jours);
            if (rows.isEmpty()) return "Aucune collecte confirmée sur les " + jours + " dernier(s) jour(s).";
            long totNb  = rows.stream().mapToLong(r -> toLong(r.get("nb"))).sum();
            long totMnt = rows.stream().mapToLong(r -> toLong(r.get("tot"))).sum();
            var sb = new StringBuilder("Collectes ").append(jours).append("j : ")
                .append(totNb).append(" opérations — ").append(fcfa(totMnt)).append("\n");
            rows.forEach(r -> sb.append("• ").append(r.get("agent"))
                .append(" : ").append(r.get("nb")).append(" collectes — ").append(fcfa(r.get("tot"))).append("\n"));
            return sb.toString();
        } catch (Exception e) { return "Données collectes indisponibles."; }
    }

    private String toolDossiers(Long imfId, String statut) {
        if (imfId == null) return "Non disponible pour le rôle SUPPORT.";
        String cond = "EN_COURS".equals(statut) ? "AND dr.statut NOT IN ('CLOS','ABANDON')" : "";
        try {
            var rows = jdbc.queryForList(
                "SELECT dr.client_id_externe, dr.statut, dr.priorite_scoring," +
                "  dr.montant_total_creances, ci.nom_complet," +
                "  COALESCE(dr.prochaine_action,'Non définie') action" +
                " FROM app.dossiers_recouvrement dr" +
                " LEFT JOIN app.clients_informels ci ON ci.client_id_externe=dr.client_id_externe AND ci.imf_id=dr.imf_id" +
                " WHERE dr.imf_id=? " + cond +
                " ORDER BY dr.priorite_scoring DESC NULLS LAST LIMIT 10", imfId);
            if (rows.isEmpty()) return "Aucun dossier de recouvrement actif.";
            var sb = new StringBuilder("Dossiers recouvrement (").append(rows.size()).append(") :\n");
            rows.forEach(r -> sb.append("• ").append(r.get("client_id_externe"))
                .append(" — ").append(r.getOrDefault("nom_complet", "?"))
                .append(" | ").append(r.get("statut"))
                .append(" | Prio=").append(r.getOrDefault("priorite_scoring", "?"))
                .append(" | ").append(fcfa(r.get("montant_total_creances")))
                .append(" | ").append(r.get("action")).append("\n"));
            return sb.toString();
        } catch (Exception e) { return "Dossiers recouvrement indisponibles."; }
    }

    private String toolAlertes(Long imfId) {
        var sb = new StringBuilder();
        if (imfId != null) {
            try {
                var rows = jdbc.queryForList(
                    "SELECT type_alerte, message, niveau_urgence FROM ml.alertes_predictives" +
                    " WHERE imf_id=? AND statut='OUVERTE' ORDER BY niveau_urgence DESC, created_at DESC LIMIT 10", imfId);
                if (!rows.isEmpty()) {
                    sb.append("Alertes ML actives (").append(rows.size()).append(") :\n");
                    rows.forEach(r -> sb.append("• [").append(r.get("niveau_urgence")).append("] ")
                        .append(r.get("type_alerte")).append(" — ").append(r.get("message")).append("\n"));
                }
            } catch (Exception ignored) {}
            try {
                var rows = jdbc.queryForList(
                    "SELECT type_alerte, message FROM app.alertes" +
                    " WHERE imf_id=? AND statut='ACTIVE' ORDER BY created_at DESC LIMIT 5", imfId);
                if (!rows.isEmpty()) {
                    sb.append("Alertes opérationnelles (").append(rows.size()).append(") :\n");
                    rows.forEach(r -> sb.append("• ").append(r.get("type_alerte"))
                        .append(" — ").append(r.get("message")).append("\n"));
                }
            } catch (Exception ignored) {}
        }
        return sb.isEmpty() ? "Aucune alerte active en ce moment." : sb.toString();
    }

    private String toolAgents(Long imfId) {
        if (imfId == null) return "Non disponible pour le rôle SUPPORT.";
        try {
            var rows = jdbc.queryForList(
                "SELECT u.prenom||' '||u.nom agent," +
                "  COUNT(ce.id) nb, COALESCE(SUM(ce.montant_collecte),0) tot," +
                "  COUNT(DISTINCT ce.client_id) clients" +
                " FROM app.utilisateurs u" +
                " LEFT JOIN app.collectes_epargne ce ON ce.agent_id=u.id" +
                "  AND ce.date_collecte >= CURRENT_DATE-INTERVAL '30 days' AND ce.statut='CONFIRMEE'" +
                " WHERE u.imf_id=? AND u.role='AGENT' AND u.actif=true" +
                " GROUP BY u.prenom,u.nom ORDER BY tot DESC", imfId);
            if (rows.isEmpty()) return "Aucun agent actif trouvé.";
            var sb = new StringBuilder("Performance agents (30j) :\n");
            rows.forEach(r -> sb.append("• ").append(r.get("agent"))
                .append(" : ").append(r.get("nb")).append(" collectes")
                .append(" | ").append(r.get("clients")).append(" clients")
                .append(" | ").append(fcfa(r.get("tot"))).append("\n"));
            return sb.toString();
        } catch (Exception e) { return "Données agents indisponibles."; }
    }

    private String toolTickets() {
        try {
            var rows = jdbc.queryForList(
                "SELECT titre, priorite, statut, categorie FROM app.tickets_support" +
                " WHERE statut IN ('OUVERT','EN_COURS')" +
                " ORDER BY CASE priorite WHEN 'CRITIQUE' THEN 1 WHEN 'HAUTE' THEN 2 WHEN 'NORMALE' THEN 3 ELSE 4 END," +
                " created_at DESC LIMIT 10");
            if (rows.isEmpty()) return "Aucun ticket ouvert en ce moment.";
            var sb = new StringBuilder("Tickets ouverts (").append(rows.size()).append(") :\n");
            rows.forEach(r -> sb.append("• [").append(r.get("priorite")).append("] ")
                .append(r.get("titre")).append(" — ").append(r.get("categorie"))
                .append(" (").append(r.get("statut")).append(")\n"));
            return sb.toString();
        } catch (Exception e) { return "Tickets indisponibles."; }
    }

    // ─── Contexte initial du system prompt ───────────────────────────────────

    private String buildContexteInitial(User user, Long imfId) {
        var sb = new StringBuilder("=== CONTEXTE ===\n");
        sb.append("Rôle : ").append(user != null ? user.getRole() : "INCONNU").append("\n");
        sb.append("Date : ").append(java.time.LocalDate.now()).append("\n");
        if (imfId == null) {
            sb.append("Vue : SUPER_ADMIN / SUPPORT (multi-IMF)\n");
            try {
                sb.append("IMF actives : ")
                  .append(jdbc.queryForObject("SELECT COUNT(*) FROM app.imf WHERE actif=true", Long.class))
                  .append("\n");
            } catch (Exception ignored) {}
            try {
                sb.append("Tickets ouverts : ")
                  .append(jdbc.queryForObject(
                      "SELECT COUNT(*) FROM app.tickets_support WHERE statut IN ('OUVERT','EN_COURS')", Long.class))
                  .append("\n");
            } catch (Exception ignored) {}
        } else {
            try {
                var imf = jdbc.queryForMap("SELECT nom, code_imf FROM app.imf WHERE id=?", imfId);
                sb.append("IMF : ").append(imf.get("nom")).append(" (").append(imf.get("code_imf")).append(")\n");
            } catch (Exception ignored) {}
            try {
                var k = jdbc.queryForMap(
                    "SELECT COUNT(*) n, ROUND(CAST(AVG(score_mcrs) AS NUMERIC),3) avg," +
                    "  SUM(CASE WHEN niveau_risque='CRITIQUE' THEN 1 ELSE 0 END) crit" +
                    " FROM ml.client_scores WHERE imf_id=?", imfId);
                sb.append("Clients scorés : ").append(k.get("n"))
                  .append(" | MCRS moy : ").append(k.get("avg"))
                  .append(" | Critiques : ").append(k.get("crit")).append("\n");
            } catch (Exception ignored) {}
        }
        sb.append("(Utilise les outils pour des données détaillées.)\n=== FIN ===\n");
        return sb.toString();
    }

    // ─── Utilitaires ─────────────────────────────────────────────────────────

    private List<Map<String, Object>> buildMessages(String system, List<ChatMessage> history) {
        var msgs = new ArrayList<Map<String, Object>>();
        msgs.add(Map.of("role", "system", "content", system));
        history.forEach(m -> msgs.add(Map.of("role", m.role(), "content", m.content())));
        return msgs;
    }

    private ResponseEntity<ApiResponse<String>> ok(String data) {
        return ResponseEntity.ok(ApiResponse.<String>builder()
            .success(true).data(data).timestamp(java.time.OffsetDateTime.now()).build());
    }

    private String fmt(Object v) {
        return v == null ? "N/A" : String.format("%.2f", toDouble(v));
    }

    private String fcfa(Object v) {
        if (v == null) return "N/A";
        long val = toLong(v);
        if (val >= 1_000_000) return String.format("%.1f M FCFA", val / 1_000_000.0);
        if (val >= 1_000)     return String.format("%,d K FCFA", val / 1_000);
        return val + " FCFA";
    }

    private String str(Map<String, Object> m, String k, String def) {
        Object v = m.get(k); return v != null ? v.toString() : def;
    }

    private double toDouble(Object v) { return v instanceof Number n ? n.doubleValue() : 0.0; }
    private long   toLong(Object v)   { return v instanceof Number n ? n.longValue()   : 0L;  }
    private int    toInt(Object v, int def) { return v instanceof Number n ? n.intValue() : def; }
}
