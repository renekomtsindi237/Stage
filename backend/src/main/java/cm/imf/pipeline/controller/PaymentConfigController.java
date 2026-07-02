package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.security.ApiKeyEncryptionService;
import cm.imf.pipeline.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Configuration des APIs de paiement mobile money — Orange Money et MTN MoMo.
 *
 * Accès : DSI de l'IMF uniquement.
 * Secrets (API keys, client secrets) : chiffrés AES-256-GCM avant stockage.
 * La réponse GET masque toujours les valeurs sensibles (on ne retourne jamais
 * un secret en clair, même au DSI).
 *
 * Table : app.imf_payment_config (créée en V56)
 */
@Slf4j
@RestController
@RequestMapping("/admin/payment-config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DSI')")
@Tag(name = "Payment Config", description = "Configuration Orange Money & MTN MoMo — DSI")
public class PaymentConfigController {

    private final JdbcTemplate           jdbc;
    private final ApiKeyEncryptionService enc;

    // ── DTOs ──────────────────────────────────────────────────────────────────

    /** Requête de mise à jour (tous les champs optionnels — PATCH partiel). */
    record PaymentConfigRequest(
            // MTN MoMo
            Boolean mtnActif,
            String  mtnBaseUrl,
            String  mtnEnvironment,
            String  mtnApiUser,
            String  mtnApiKey,                        // plaintext → chiffré
            String  mtnSubscriptionKeyCollection,     // plaintext → chiffré
            String  mtnSubscriptionKeyDisbursement,   // plaintext → chiffré
            String  mtnCallbackUrl,
            // Orange Money
            Boolean orangeActif,
            String  orangeBaseUrl,
            String  orangeEnvironment,
            String  orangeMerchantKey,                // plaintext → chiffré
            String  orangeClientId,
            String  orangeClientSecret,               // plaintext → chiffré
            String  orangeMerchantCode,
            String  orangeReturnUrl,
            String  orangeCancelUrl,
            String  orangeNotifUrl
    ) {}

    /** Réponse publique — secrets masqués, jamais de valeur en clair. */
    record PaymentConfigResponse(
            // MTN MoMo
            Boolean mtnActif,
            String  mtnBaseUrl,
            String  mtnEnvironment,
            String  mtnApiUser,
            String  mtnApiKeyMasked,
            String  mtnSubscriptionKeyCollectionMasked,
            String  mtnSubscriptionKeyDisbursementMasked,
            String  mtnCallbackUrl,
            // Orange Money
            Boolean orangeActif,
            String  orangeBaseUrl,
            String  orangeEnvironment,
            String  orangeMerchantKeyMasked,
            String  orangeClientId,
            String  orangeClientSecretMasked,
            String  orangeMerchantCode,
            String  orangeReturnUrl,
            String  orangeCancelUrl,
            String  orangeNotifUrl,
            // Audit
            String  updatedAt
    ) {}

    // ── GET /api/v1/admin/payment-config ─────────────────────────────────────

    @Operation(summary = "Lire la configuration paiement de l'IMF (secrets masqués)")
    @GetMapping
    public ResponseEntity<ApiResponse<PaymentConfigResponse>> get() {
        Long imfId = TenantContext.currentImfId();
        ensureRow(imfId);

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT mtn_actif, mtn_base_url, mtn_environment, mtn_api_user,
                       mtn_subscription_key_collection_masked,
                       mtn_subscription_key_disbursement_masked,
                       mtn_callback_url,
                       orange_actif, orange_base_url, orange_environment,
                       orange_merchant_key_masked, orange_client_id,
                       orange_client_secret_masked, orange_merchant_code,
                       orange_return_url, orange_cancel_url, orange_notif_url,
                       updated_at
                FROM app.imf_payment_config
                WHERE imf_id = ?
                """, imfId);

        // mtn_api_key n'a pas de colonne "masked" directement — on lit si présent
        String mtnApiKeyMasked = queryMasked(imfId, "mtn_api_key_encrypted");

        PaymentConfigResponse resp = new PaymentConfigResponse(
                (Boolean) row.get("mtn_actif"),
                (String) row.get("mtn_base_url"),
                (String) row.get("mtn_environment"),
                (String) row.get("mtn_api_user"),
                mtnApiKeyMasked,
                (String) row.get("mtn_subscription_key_collection_masked"),
                (String) row.get("mtn_subscription_key_disbursement_masked"),
                (String) row.get("mtn_callback_url"),
                (Boolean) row.get("orange_actif"),
                (String) row.get("orange_base_url"),
                (String) row.get("orange_environment"),
                (String) row.get("orange_merchant_key_masked"),
                (String) row.get("orange_client_id"),
                (String) row.get("orange_client_secret_masked"),
                (String) row.get("orange_merchant_code"),
                (String) row.get("orange_return_url"),
                (String) row.get("orange_cancel_url"),
                (String) row.get("orange_notif_url"),
                row.get("updated_at") != null ? row.get("updated_at").toString() : null
        );

        return ResponseEntity.ok(ApiResponse.ok(resp));
    }

    // ── PUT /api/v1/admin/payment-config ─────────────────────────────────────

    @Operation(summary = "Mettre à jour la configuration paiement (secrets chiffrés AES-256-GCM)")
    @PutMapping
    public ResponseEntity<ApiResponse<PaymentConfigResponse>> update(
            @RequestBody PaymentConfigRequest req) {

        Long   imfId  = TenantContext.currentImfId();
        String actor  = TenantContext.currentUser().getUsername();
        ensureRow(imfId);

        // ── MTN MoMo ─────────────────────────────────────────────────────────
        if (req.mtnActif()       != null) set(imfId, "mtn_actif",       req.mtnActif());
        if (req.mtnBaseUrl()     != null) set(imfId, "mtn_base_url",    req.mtnBaseUrl());
        if (req.mtnEnvironment() != null) set(imfId, "mtn_environment", req.mtnEnvironment());
        if (req.mtnApiUser()     != null) set(imfId, "mtn_api_user",    req.mtnApiUser());
        if (req.mtnCallbackUrl() != null) set(imfId, "mtn_callback_url", req.mtnCallbackUrl());

        if (req.mtnApiKey() != null && !req.mtnApiKey().isBlank()) {
            setEncrypted(imfId, "mtn_api_key_encrypted", req.mtnApiKey());
        }
        if (req.mtnSubscriptionKeyCollection() != null
                && !req.mtnSubscriptionKeyCollection().isBlank()) {
            setEncryptedWithMask(imfId,
                    "mtn_subscription_key_collection_enc",
                    "mtn_subscription_key_collection_masked",
                    req.mtnSubscriptionKeyCollection());
        }
        if (req.mtnSubscriptionKeyDisbursement() != null
                && !req.mtnSubscriptionKeyDisbursement().isBlank()) {
            setEncryptedWithMask(imfId,
                    "mtn_subscription_key_disbursement_enc",
                    "mtn_subscription_key_disbursement_masked",
                    req.mtnSubscriptionKeyDisbursement());
        }

        // ── Orange Money ─────────────────────────────────────────────────────
        if (req.orangeActif()         != null) set(imfId, "orange_actif",         req.orangeActif());
        if (req.orangeBaseUrl()       != null) set(imfId, "orange_base_url",       req.orangeBaseUrl());
        if (req.orangeEnvironment()   != null) set(imfId, "orange_environment",    req.orangeEnvironment());
        if (req.orangeClientId()      != null) set(imfId, "orange_client_id",      req.orangeClientId());
        if (req.orangeMerchantCode()  != null) set(imfId, "orange_merchant_code",  req.orangeMerchantCode());
        if (req.orangeReturnUrl()     != null) set(imfId, "orange_return_url",     req.orangeReturnUrl());
        if (req.orangeCancelUrl()     != null) set(imfId, "orange_cancel_url",     req.orangeCancelUrl());
        if (req.orangeNotifUrl()      != null) set(imfId, "orange_notif_url",      req.orangeNotifUrl());

        if (req.orangeMerchantKey() != null && !req.orangeMerchantKey().isBlank()) {
            setEncryptedWithMask(imfId,
                    "orange_merchant_key_enc",
                    "orange_merchant_key_masked",
                    req.orangeMerchantKey());
        }
        if (req.orangeClientSecret() != null && !req.orangeClientSecret().isBlank()) {
            setEncryptedWithMask(imfId,
                    "orange_client_secret_enc",
                    "orange_client_secret_masked",
                    req.orangeClientSecret());
        }

        // Audit
        jdbc.update("""
                UPDATE app.imf_payment_config
                SET updated_by_username = ?, updated_at = ?
                WHERE imf_id = ?
                """, actor, OffsetDateTime.now(), imfId);

        log.info("Config paiement mise à jour par {} pour IMF {}", actor, imfId);
        return get();
    }

    // ── Helpers privés ────────────────────────────────────────────────────────

    /** Crée une ligne vide si elle n'existe pas encore. */
    private void ensureRow(Long imfId) {
        jdbc.update("""
                INSERT INTO app.imf_payment_config (imf_id)
                VALUES (?) ON CONFLICT (imf_id) DO NOTHING
                """, imfId);
    }

    private void set(Long imfId, String col, Object val) {
        jdbc.update("UPDATE app.imf_payment_config SET " + col + " = ? WHERE imf_id = ?",
                val, imfId);
    }

    /** Chiffre la valeur et met à jour uniquement la colonne chiffrée. */
    private void setEncrypted(Long imfId, String encCol, String plain) {
        jdbc.update("UPDATE app.imf_payment_config SET " + encCol + " = ? WHERE imf_id = ?",
                enc.encrypt(plain), imfId);
    }

    /** Chiffre la valeur ET met à jour le masque (8 premiers + '...'). */
    private void setEncryptedWithMask(Long imfId, String encCol, String maskCol, String plain) {
        String mask = plain.length() > 8
                ? plain.substring(0, 8) + "..."
                : "***";
        jdbc.update("""
                UPDATE app.imf_payment_config
                SET %s = ?, %s = ?
                WHERE imf_id = ?
                """.formatted(encCol, maskCol),
                enc.encrypt(plain), mask, imfId);
    }

    /** Retourne le masque d'un champ chiffré (non null → configué). */
    private String queryMasked(Long imfId, String encCol) {
        return jdbc.queryForList(
                "SELECT " + encCol + " FROM app.imf_payment_config WHERE imf_id = ?",
                imfId).stream()
                .findFirst()
                .map(r -> r.get(encCol) != null ? "••••••••..." : null)
                .orElse(null);
    }
}
