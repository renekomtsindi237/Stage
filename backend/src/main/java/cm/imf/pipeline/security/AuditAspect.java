package cm.imf.pipeline.security;

import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.service.IAuditTrailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.StandardTypeLocator;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * Aspect Spring AOP qui intercepte les méthodes annotées {@link Auditable}
 * et enregistre une entrée dans la piste d'audit immuable (app.audit_trail).
 *
 * Fonctionnalités :
 *   - Capture de l'ancienne valeur AVANT l'appel (ancienneValeurExpression)
 *   - Capture du résultat APRÈS l'appel (captureResult = true)
 *   - Variable SpEL #currentUserId disponible dans toutes les expressions
 *   - Résolution de beans Spring via @nomBean dans les expressions SpEL
 *   - Enregistrement ECHEC automatique en cas d'exception
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final IAuditTrailService auditTrailService;
    private final ObjectMapper       objectMapper;
    private final ApplicationContext applicationContext;

    private static final ExpressionParser SPEL = new SpelExpressionParser();

    @Around("@annotation(auditable)")
    public Object intercepter(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        String ipClient  = extraireIp();
        String userAgent = extraireUserAgent();

        // Contexte SpEL enrichi : paramètres de la méthode + beans Spring + #currentUserId
        StandardEvaluationContext spelCtx = construireContexteSpel(pjp);

        String entiteId = evaluerSpel(auditable.entiteIdExpression(), spelCtx, String.class);
        String motif    = evaluerSpel(auditable.motifExpression(),    spelCtx, String.class);

        // Capturer l'ancienne valeur AVANT l'appel (pour les MODIFICATION / CHANGEMENT_STATUT)
        Map<String, Object> ancienneValeur = null;
        if (!auditable.ancienneValeurExpression().isBlank()) {
            Object avant = evaluerSpel(auditable.ancienneValeurExpression(), spelCtx, Object.class);
            ancienneValeur = toMap(avant);
        }

        try {
            Object result = pjp.proceed();

            Map<String, Object> nouvelleValeur = null;
            if (auditable.captureResult() && result != null) {
                nouvelleValeur = toMap(result);
            }

            auditTrailService.enregistrer(
                    auditable.action(),
                    auditable.entiteType(),
                    entiteId,
                    ancienneValeur,
                    nouvelleValeur,
                    motif,
                    ipClient,
                    userAgent
            );

            return result;

        } catch (Exception ex) {
            auditTrailService.enregistrerEchec(
                    auditable.action(),
                    auditable.entiteType(),
                    entiteId,
                    ex.getMessage(),
                    ipClient
            );
            throw ex;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private StandardEvaluationContext construireContexteSpel(ProceedingJoinPoint pjp) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();

        // Résolution des beans Spring : @nomBean → applicationContext.getBean("nomBean")
        ctx.setBeanResolver((evalCtx, beanName) -> applicationContext.getBean(beanName));

        // Paramètres de la méthode
        MethodSignature sig    = (MethodSignature) pjp.getSignature();
        Parameter[]     params = sig.getMethod().getParameters();
        Object[]        args   = pjp.getArgs();
        for (int i = 0; i < params.length; i++) {
            ctx.setVariable(params[i].getName(), args[i]);
        }

        // Variable #currentUserId — utile pour les méthodes sans paramètre d'identité
        User currentUser = TenantContext.currentUser();
        if (currentUser != null) {
            ctx.setVariable("currentUserId",   String.valueOf(currentUser.getId()));
            ctx.setVariable("currentUsername", currentUser.getUsername());
            ctx.setVariable("currentImfId",    currentUser.getImf() != null
                                                ? String.valueOf(currentUser.getImf().getId()) : null);
        }

        return ctx;
    }

    private <T> T evaluerSpel(String expression, StandardEvaluationContext ctx, Class<T> type) {
        if (expression == null || expression.isBlank()) return null;
        try {
            return SPEL.parseExpression(expression).getValue(ctx, type);
        } catch (Exception e) {
            log.debug("AuditAspect: erreur SpEL '{}' : {}", expression, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object obj) {
        if (obj == null) return null;
        try {
            // Si c'est un ResponseEntity, extraire le body
            if (obj instanceof org.springframework.http.ResponseEntity<?> re) {
                obj = re.getBody();
                if (obj == null) return null;
                // Si le body est un ApiResponse, extraire data
                if (obj instanceof cm.imf.pipeline.dto.response.ApiResponse<?> ar) {
                    obj = ar.getData();
                    if (obj == null) return null;
                }
            }
            return objectMapper.convertValue(obj, Map.class);
        } catch (Exception e) {
            log.debug("AuditAspect: toMap impossible sur {} : {}", obj.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private String extraireIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest req = attrs.getRequest();
            String forwarded = req.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
            return req.getRemoteAddr();
        } catch (Exception e) { return null; }
    }

    private String extraireUserAgent() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            String ua = attrs.getRequest().getHeader("User-Agent");
            return ua != null && ua.length() > 500 ? ua.substring(0, 500) : ua;
        } catch (Exception e) { return null; }
    }
}
