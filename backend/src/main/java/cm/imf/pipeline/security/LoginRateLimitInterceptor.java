package cm.imf.pipeline.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Limite les tentatives de connexion par IP sur une fenêtre glissante.
 * Les seuils sont configurables via application.yml (app.security.rate-limit.*).
 *
 * Protège /api/auth/login et /api/auth/refresh.
 * Pour une restriction plus fine par IMF, voir Imf.maxTentativesConnexion
 * (utilisé par AuthService pour le verrouillage de compte).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginRateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.security.rate-limit.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.security.rate-limit.window-seconds:60}")
    private long windowSeconds;

    private static final String KEY_PREFIX = "rate:login:";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        try {
            String ip  = resolveClientIp(request);
            String key = KEY_PREFIX + ip;

            Long attempts = redisTemplate.opsForValue().increment(key);
            if (attempts != null && attempts == 1L) {
                redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
            }

            if (attempts != null && attempts > maxAttempts) {
                log.warn("Rate limit dépassé — ip={} tentatives={} max={}", ip, attempts, maxAttempts);
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                    "{\"code\":\"TOO_MANY_REQUESTS\"," +
                    "\"message\":\"Trop de tentatives. Réessayez dans " + windowSeconds + " secondes.\"}"
                );
                return false;
            }
        } catch (Exception e) {
            log.warn("Rate limiter indisponible (Redis), requête autorisée : {}", e.getMessage());
        }

        return true;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
