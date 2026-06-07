package cm.imf.pipeline.exception;

import cm.imf.pipeline.dto.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("GlobalExceptionHandler — tests unitaires")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("ResourceNotFoundException → 404 avec message métier")
    void handleResourceNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Prêt", "PRE-001");
        ResponseEntity<ApiResponse<Void>> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("Prêt");
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    @DisplayName("DuplicateResourceException → 409 CONFLICT")
    void handleDuplicateResource() {
        DuplicateResourceException ex = new DuplicateResourceException("Utilisateur", "username", "jdoe");
        ResponseEntity<ApiResponse<Void>> response = handler.handleDuplicate(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    @DisplayName("InvalidTokenException → 401 UNAUTHORIZED")
    void handleInvalidToken() {
        InvalidTokenException ex = new InvalidTokenException("Token invalide ou expiré");
        ResponseEntity<ApiResponse<Void>> response = handler.handleInvalidToken(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("BadCredentialsException → 401 message générique (pas d'indice)")
    void handleBadCredentials() {
        BadCredentialsException ex = new BadCredentialsException("Bad credentials");
        ResponseEntity<ApiResponse<Void>> response = handler.handleBadCredentials(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Le message ne doit pas mentionner "mot de passe incorrect" (anti-timing-attack)
        assertThat(response.getBody().getMessage()).doesNotContainIgnoringCase("mot de passe");
        assertThat(response.getBody().getMessage()).containsIgnoringCase("invalide");
    }

    @Test
    @DisplayName("AccessDeniedException → 403 FORBIDDEN")
    void handleAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("Accès refusé");
        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("MethodArgumentNotValidException → 400 avec map des erreurs de champs")
    void handleValidation() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult br = mock(BindingResult.class);
        FieldError fieldError = new FieldError("obj", "username", "ne doit pas être vide");
        when(ex.getBindingResult()).thenReturn(br);
        when(br.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ApiResponse<Map<String, String>>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getData()).containsKey("username");
        assertThat(response.getBody().getData().get("username")).contains("vide");
    }

    @Test
    @DisplayName("Exception générique → 500 INTERNAL_SERVER_ERROR")
    void handleGenericException() {
        Exception ex = new RuntimeException("Erreur inattendue");
        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneric(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    @DisplayName("IllegalArgumentException → 400 BAD_REQUEST")
    void handleIllegalArgument() {
        IllegalArgumentException ex = new IllegalArgumentException("Argument invalide");
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("Argument invalide");
    }
}
