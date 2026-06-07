package cm.imf.pipeline.validation;

import cm.imf.pipeline.dto.request.*;
import cm.imf.pipeline.enums.CanalPaiement;
import cm.imf.pipeline.enums.NiveauKyc;
import cm.imf.pipeline.enums.Role;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests de validation des DTOs — Bean Validation (jakarta.validation).
 * Garantit que les contraintes @NotBlank/@NotNull/@Min/@DecimalMin sont respectées.
 */
@DisplayName("Validation DTOs — contraintes Bean Validation")
class ValidationConstraintsTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private <T> Set<ConstraintViolation<T>> validate(T obj) {
        return validator.validate(obj);
    }

    private <T> void assertNoViolations(T obj) {
        assertThat(validate(obj)).isEmpty();
    }

    private <T> void assertHasViolations(T obj) {
        assertThat(validate(obj)).isNotEmpty();
    }

    // ── LoginRequest ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("LoginRequest")
    class LoginRequestValidation {

        @Test
        @DisplayName("Valide quand username et password non vides")
        void valide() {
            assertNoViolations(new LoginRequest("jkamga", "Pass123!"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Username null ou vide → violation")
        void username_invalide(String username) {
            assertHasViolations(new LoginRequest(username, "Pass123!"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Password null ou vide → violation")
        void password_invalide(String password) {
            assertHasViolations(new LoginRequest("jkamga", password));
        }
    }

    // ── CollecteRequest ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("CollecteRequest")
    class CollecteRequestValidation {

        @Test
        @DisplayName("Valide avec tous les champs requis")
        void valide() {
            assertNoViolations(new CollecteRequest(
                    "MOBILE-001", "CLI-001", "PRE-001",
                    LocalDate.now(), new BigDecimal("25000"),
                    CanalPaiement.MTN, "REF-001", null, null, null));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("idCollecteMobile null/vide → violation @NotBlank")
        void idMobile_invalide(String id) {
            assertHasViolations(new CollecteRequest(
                    id, "CLI-001", "PRE-001",
                    LocalDate.now(), new BigDecimal("25000"),
                    CanalPaiement.MTN, null, null, null, null));
        }

        @Test
        @DisplayName("montantCollecte null → violation @NotNull")
        void montant_null_violation() {
            assertHasViolations(new CollecteRequest(
                    "MOBILE-001", "CLI-001", "PRE-001",
                    LocalDate.now(), null,
                    CanalPaiement.MTN, null, null, null, null));
        }
    }

    // ── InitierKycRequest ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("InitierKycRequest")
    class InitierKycRequestValidation {

        @Test
        @DisplayName("Valide — clientId + nomClient + niveauDemande présents")
        void valide() {
            assertNoViolations(new InitierKycRequest(
                    "CLI-001", "Kouam", null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, null, null, null, null,
                    NiveauKyc.NIVEAU_1, false, null));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("clientId blank → violation")
        void clientId_blank(String id) {
            assertHasViolations(new InitierKycRequest(
                    id, "Kouam", null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, null, null, null, null,
                    NiveauKyc.NIVEAU_1, false, null));
        }

        @Test
        @DisplayName("niveauDemande null → violation @NotNull")
        void niveauDemande_null() {
            assertHasViolations(new InitierKycRequest(
                    "CLI-001", "Kouam", null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, null, null, null, null,
                    null, false, null));
        }
    }

    // ── OuvrirDossierRequest ──────────────────────────────────────────────────

    @Nested
    @DisplayName("OuvrirDossierRequest")
    class OuvrirDossierRequestValidation {

        @Test
        @DisplayName("Valide — idPret + montantImpaye + joursRetard présents")
        void valide() {
            assertNoViolations(new OuvrirDossierRequest(
                    "PRE-001", "Client X",
                    new BigDecimal("300000"), 90,
                    null, null, null, null, null));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("idPret vide → violation @NotBlank")
        void idPret_vide(String id) {
            assertHasViolations(new OuvrirDossierRequest(
                    id, "Client X", new BigDecimal("300000"), 90,
                    null, null, null, null, null));
        }

        @Test
        @DisplayName("montantImpaye=0 → violation @DecimalMin(0.01)")
        void montant_zero_violation() {
            assertHasViolations(new OuvrirDossierRequest(
                    "PRE-001", "Client", BigDecimal.ZERO, 90,
                    null, null, null, null, null));
        }

        @Test
        @DisplayName("joursRetard=0 → violation @Min(1)")
        void joursRetard_zero_violation() {
            assertHasViolations(new OuvrirDossierRequest(
                    "PRE-001", "Client", new BigDecimal("100000"), 0,
                    null, null, null, null, null));
        }
    }

    // ── CreateUserRequest ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("CreateUserRequest")
    class CreateUserRequestValidation {

        @Test
        @DisplayName("Valide — username + password + role présents")
        void valide() {
            assertNoViolations(new CreateUserRequest(
                    "newuser", "SecurePass!1", Role.AGENT, "YD001"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("username vide → violation")
        void username_vide(String u) {
            assertHasViolations(new CreateUserRequest(u, "SecurePass!1", Role.AGENT, null));
        }

        @Test
        @DisplayName("role null → violation @NotNull")
        void role_null() {
            assertHasViolations(new CreateUserRequest("user", "Pass!1", null, null));
        }
    }
}
