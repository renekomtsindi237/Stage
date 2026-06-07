package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Endpoint utilitaire pour l'internationalisation côté frontend.
 *
 * Permet au client Angular de vérifier les langues disponibles
 * et de récupérer les messages traduits si besoin.
 *
 * Dans la plupart des cas, les traductions frontend sont gérées
 * par ngx-translate (fichiers JSON locaux). Ce contrôleur sert
 * surtout pour les messages d'erreur backend qui remontent dans
 * les réponses ApiResponse.
 *
 * Accès : authentifié (anyRequest().authenticated() dans SecurityConfig)
 */
@RestController
@RequestMapping("/lang")
@Tag(name = "Internationalisation", description = "Gestion de la langue — fr / en")
public class LangController {

    private final MessageSource messageSource;

    public LangController(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Retourne la liste des langues supportées par l'API.
     * Utilisé par le LangToggleComponent Angular pour afficher les options.
     */
    @Operation(summary = "Langues supportées par le backend")
    @GetMapping("/supported")
    public ResponseEntity<ApiResponse<List<String>>> getSupportedLocales() {
        return ResponseEntity.ok(ApiResponse.ok(List.of("fr", "en")));
    }

    /**
     * Retourne un message traduit pour une clé donnée.
     * Utile pour déboguer les traductions en développement.
     *
     * Exemple : GET /api/lang/message?key=auth.login.success&lang=en
     */
    @Operation(summary = "Résolution d'un message i18n (debug)")
    @GetMapping("/message")
    public ResponseEntity<ApiResponse<Map<String, String>>> getMessage(
            @RequestParam String key,
            Locale locale) {
        String value = messageSource.getMessage(key, null, key, locale);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("key", key, "value", value, "locale", locale.getLanguage())));
    }
}
