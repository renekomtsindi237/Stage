package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Requête PATCH /api/users/me/preferences.
 * Tous les champs sont optionnels (null = ne pas modifier).
 * Seules les valeurs non-null sont appliquées (patch partiel).
 */
public record UpdatePreferencesRequest(

        /** Thème visuel : light | dark | auto. */
        @Pattern(regexp = "^(light|dark|auto)$", message = "Thème : light, dark ou auto")
        String prefTheme,

        /** Langue de l'interface : fr | en. */
        @Pattern(regexp = "^(fr|en)$", message = "Langue supportée : fr ou en")
        String prefLangue,

        /** Maître-switch : désactiver toutes les notifications. */
        Boolean notificationsActives,

        /** Recevoir les alertes d'impayés (ALERTE_CREATED / ALERTE_UPDATED). */
        Boolean notifAlertes,

        /** Recevoir les confirmations de collectes terrain. */
        Boolean notifCollectes,

        /** Recevoir les fins de synchronisation hors-ligne. */
        Boolean notifSync,

        /** Recevoir les changements d'état du pipeline de données (usage DSI/technique). */
        Boolean notifPipeline,

        /** Éléments affichés par page dans les listes paginées. */
        @Min(value = 10, message = "Minimum 10 éléments par page")
        @Max(value = 100, message = "Maximum 100 éléments par page")
        Integer elementsParPage

) {}
