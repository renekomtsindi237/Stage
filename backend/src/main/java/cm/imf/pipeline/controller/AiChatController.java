package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.security.TenantContext;
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
 * Proxy IA — Groq API (gratuit, compatible OpenAI).
 * Le système prompt est enrichi avec les KPIs réels de l'IMF de l'utilisateur.
 * Variable d'environnement : GROQ_API_KEY
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
public class AiChatController {

    // Fix compilation issues caused by overloaded ApiResponse.ok() generic inference.
    // We explicitly build ApiResponse<String> for every return path.


    private final JdbcTemplate jdbc;

    @Value("${imf.ai.api-key:}")
    private String apiKey;

    @Value("${imf.ai.base-url:https://api.groq.com/openai/v1}")
    private String baseUrl;

    @Value("${imf.ai.model:llama-3.3-70b-versatile}")
    private String model;

    @Value("${imf.ai.max-tokens:1024}")
    private int maxTokens;

    private static final String BASE_SYSTEM =
        "Tu es l'assistant IA de MicroRecouv, une plateforme de microfinance camerounaise conforme COBAC/BEAC. "
        + "Tu aides les directeurs, agents et analystes à comprendre les données financières, "
        + "les indicateurs KPI (PAR30/PAR60/PAR90, taux de recouvrement, MCRS), les règlements COBAC/BEAC, "
        + "et les bonnes pratiques de microfinance en contexte CEMAC. "
        + "Réponds de façon concise, professionnelle et en français. "
        + "Formule des recommandations concrètes basées sur les données réelles ci-dessous.";

    record ChatMessage(String role, String content) {}
    record ChatRequest(List<ChatMessage> messages) {}

    @PostMapping("/chat")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> chat(
            @RequestBody ChatRequest req,
            @AuthenticationPrincipal User user) {

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GROQ_API_KEY non configuré");
            return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("L'assistant IA n'est pas encore configuré. Contactez votre DSI pour activer la clé API GROQ.")
                .timestamp(java.time.OffsetDateTime.now())
                .build());
        }

        try {
            String systemPrompt = BASE_SYSTEM + "\n\n" + buildContexte(user);

            var messages = new ArrayList<Map<String, String>>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            for (var m : req.messages()) {
                messages.add(Map.of("role", m.role(), "content", m.content()));
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("max_tokens", maxTokens);
            body.put("stream", false);

            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            var rt = new RestTemplate();
            var response = rt.exchange(
                baseUrl + "/chat/completions",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
            );

            @SuppressWarnings("unchecked")
            var choices = (List<Map<String, Object>>) response.getBody().get("choices");
            @SuppressWarnings("unchecked")
            var message = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");

            return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .data(content)
                .timestamp(java.time.OffsetDateTime.now())
                .build());

        } catch (Exception e) {
            log.error("Erreur appel IA : {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Désolé, je rencontre une difficulté technique. Réessayez dans quelques instants.")
                .timestamp(java.time.OffsetDateTime.now())
                .build());
        }
    }

    /** Construit le contexte réel de l'IMF pour enrichir le système prompt */
    private String buildContexte(User user) {
        Long imfId = TenantContext.currentImfId();
        if (imfId == null) return "";

        StringBuilder ctx = new StringBuilder("=== DONNÉES RÉELLES DE L'IMF ===\n");

        try {
            Map<String, Object> kpi = jdbc.queryForMap(
                "SELECT COUNT(*) AS nb_clients, COALESCE(AVG(score_mcrs),0) AS score_moyen " +
                "FROM ml.client_scores WHERE imf_id = ?", imfId);
            ctx.append("Clients scorés : ").append(kpi.get("nb_clients")).append("\n");
            ctx.append("Score MCRS moyen : ").append(String.format("%.1f", toDouble(kpi.get("score_moyen")))).append("\n");
        } catch (Exception ignored) {}

        try {
            List<Map<String, Object>> dist = jdbc.queryForList(
                "SELECT niveau_risque, COUNT(*) AS nb FROM ml.client_scores WHERE imf_id = ? " +
                "GROUP BY niveau_risque ORDER BY nb DESC", imfId);
            ctx.append("Distribution risque : ");
            dist.forEach(r -> ctx.append(r.get("niveau_risque")).append("=").append(r.get("nb")).append(" "));
            ctx.append("\n");
        } catch (Exception ignored) {}

        try {
            Long alertes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ml.alertes_predictives WHERE imf_id = ? AND statut = 'OUVERTE'",
                Long.class, imfId);
            ctx.append("Alertes ML ouvertes : ").append(alertes).append("\n");
        } catch (Exception ignored) {}

        try {
            Map<String, Object> par = jdbc.queryForMap(
                "SELECT * FROM app.v_par_par_imf WHERE imf_id = ?", imfId);
            ctx.append("PAR30 : ").append(par.getOrDefault("encours_par30", "N/A"))
               .append(" | PAR60 : ").append(par.getOrDefault("encours_par60", "N/A"))
               .append(" | PAR90 : ").append(par.getOrDefault("encours_par90", "N/A")).append("\n");
        } catch (Exception ignored) {}

        try {
            Long pendingKyc = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app.kyc_dossiers WHERE imf_id = ? AND statut IN ('EN_ATTENTE','COMPLEMENT_REQUIS')",
                Long.class, imfId);
            ctx.append("Dossiers KYC en attente : ").append(pendingKyc).append("\n");
        } catch (Exception ignored) {}

        ctx.append("Rôle utilisateur connecté : ").append(user != null ? user.getRole() : "INCONNU").append("\n");
        ctx.append("=== FIN DONNÉES ===\n");
        return ctx.toString();
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return 0.0;
    }
}
