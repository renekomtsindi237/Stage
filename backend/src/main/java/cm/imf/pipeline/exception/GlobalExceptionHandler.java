package cm.imf.pipeline.exception;

import cm.imf.pipeline.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gestionnaire global des exceptions REST.
 *
 * Centralise la gestion des erreurs pour éviter la duplication dans chaque
 * contrôleur. Chaque exception est traduite dans la langue demandée par
 * le client (header Accept-Language : fr ou en).
 *
 * Pattern utilisé : @RestControllerAdvice — intercepte toutes les exceptions
 * lancées par les @RestController avant que Spring ne génère une réponse HTTP.
 *
 * Ce gestionnaire garantit que toutes les erreurs respectent l'enveloppe
 * ApiResponse{success, message, data, timestamp}.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    // === Validation des champs (@Valid) ======================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex, Locale locale) {

        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalide",
                        (a, b) -> a));

        String msg = msg("error.validation", locale);
        return ResponseEntity.badRequest().body(ApiResponse.error(msg, errors));
    }

    // === Violations contraintes (path/query params) ===========================

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex, Locale locale) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    // === Type de paramètre incorrect ==========================================

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, Locale locale) {
        String message = msg("error.param.invalid", locale, ex.getName(), String.valueOf(ex.getValue()));
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    // === Ressource introuvable ================================================

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            ResourceNotFoundException ex, Locale locale) {
        // L'exception contient déjà le message métier traduit côté service.
        // On le retransmet tel quel pour la cohérence.
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    // === Doublon ==============================================================

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(
            DuplicateResourceException ex, Locale locale) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
    }

    // === Token invalide =======================================================

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidToken(
            InvalidTokenException ex, Locale locale) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // === Règles métier ========================================================

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.error(ex.getMessage()));
    }

    // === Mauvais identifiants =================================================

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(
            BadCredentialsException ex, Locale locale) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(msg("auth.credentials.invalid", locale)));
    }

    // === Compte désactivé =====================================================

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabled(DisabledException ex, Locale locale) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(msg("auth.account.disabled", locale)));
    }

    // === Accès refusé (rôle insuffisant) ======================================

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException ex, Locale locale) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(msg("auth.access.denied", locale)));
    }

    // === ResponseStatusException (throw explicite dans les services) ==========

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(
            ResponseStatusException ex, Locale locale) {
        String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return ResponseEntity.status(ex.getStatusCode()).body(ApiResponse.error(reason));
    }

    // === IllegalArgumentException (token expiré, etc.) =======================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException ex, Locale locale) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    // === Catch-all : toute exception non prévue ================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex, Locale locale) {
        log.error("Erreur non anticipée : {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(msg("error.internal", locale)));
    }

    // === Utilitaire ===========================================================

    /**
     * Résout un message i18n depuis le MessageSource.
     * Si la clé est introuvable, retourne la clé elle-même (fail-safe).
     */
    private String msg(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args, key, locale);
    }
}
