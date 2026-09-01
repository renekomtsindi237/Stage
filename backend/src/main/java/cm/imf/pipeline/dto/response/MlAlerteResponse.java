package cm.imf.pipeline.dto.response;

/**
 * Alerte prédictive MCRS. {@code createdAt} est une ISO-8601 (String) pour
 * éviter un 500 Jackson/JDBC sur OffsetDateTime selon le driver PostgreSQL.
 */
public record MlAlerteResponse(
        Long id,
        String clientIdExterne,
        String nomClient,
        String typeAlerte,
        String urgence,
        String titre,
        String description,
        String recommandation,
        String statut,
        String createdAt,
        String resolutionNote,
        Double encours,
        Integer joursRetard,
        Double scoreMcrs,
        Double probabiliteDefaut90j,
        String actionRecommandee
) {}
