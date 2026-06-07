package cm.imf.pipeline.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

/**
 * Suivi en temps réel des utilisateurs connectés via Redis.
 *
 * Stratégie : à chaque requête authentifiée, on (re)positionne une clé Redis
 * {@code online:{userId}:{imfId}} avec un TTL de 5 minutes.
 * Un utilisateur est considéré "en ligne" tant que sa clé existe en Redis.
 *
 * Comptage :
 *  - SUPER_ADMIN : toutes les clés {@code online:*}
 *  - Autres rôles : clés filtrées par {@code online:*:{imfId}}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineTrackingService {

    private static final String PREFIX  = "online:";
    private static final Duration TTL   = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;

    /**
     * Marque l'utilisateur comme actif. Appelé à chaque requête JWT valide.
     *
     * @param userId identifiant de l'utilisateur
     * @param imfId  identifiant de l'IMF (null pour SUPER_ADMIN → "0")
     */
    public void markOnline(Long userId, Long imfId) {
        String key = PREFIX + userId + ":" + (imfId != null ? imfId : "0");
        try {
            redis.opsForValue().set(key, "1", TTL);
        } catch (Exception e) {
            log.debug("Redis online tracking unavailable: {}", e.getMessage());
        }
    }

    /** Nombre total d'utilisateurs en ligne (toutes IMF confondues). */
    public long countOnline() {
        return countKeys(PREFIX + "*");
    }

    /** Nombre d'utilisateurs en ligne pour une IMF donnée. */
    public long countOnlineByImf(Long imfId) {
        return countKeys(PREFIX + "*:" + imfId);
    }

    private long countKeys(String pattern) {
        try {
            Set<String> keys = redis.keys(pattern);
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.debug("Redis scan unavailable: {}", e.getMessage());
            return 0;
        }
    }
}
