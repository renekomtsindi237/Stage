package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.LoginRequest;
import cm.imf.pipeline.dto.request.RefreshRequest;
import cm.imf.pipeline.dto.request.ResetPasswordWithTokenRequest;
import cm.imf.pipeline.dto.request.VerifyOtpRequest;
import cm.imf.pipeline.dto.response.AuthResponse;
import cm.imf.pipeline.dto.response.OtpInitResponse;
import cm.imf.pipeline.dto.response.OtpVerifyResponse;

public interface IAuthService {

    /**
     * Login SUPER_ADMIN uniquement (username + password).
     * Retourne 403 si le rôle n'est pas SUPER_ADMIN.
     */
    AuthResponse login(LoginRequest request);

    AuthResponse refresh(RefreshRequest request);

    void logout(String refreshToken);

    /**
     * Étape 1 — Envoie un OTP par email.
     * Utilisé pour la connexion ET l'activation (premier login).
     * Retourne toujours le même message (anti-énumération d'emails).
     */
    OtpInitResponse requestOtp(String email);

    /**
     * Étape 2 — Vérifie le code OTP.
     *
     * Retourne :
     *  - {@code OtpVerifyResponse.AUTHENTICATED}     → tokens complets (compte actif)
     *  - {@code OtpVerifyResponse.MUST_SET_PASSWORD} → resetToken JWT 15 min (premier login)
     *
     * @throws ResponseStatusException 400 si code invalide, expiré ou épuisé (3 tentatives)
     */
    OtpVerifyResponse verifyOtp(VerifyOtpRequest request);

    /**
     * Étape 3 — Définit le mot de passe fort après vérification OTP (premier login / activation).
     * Invalide toutes les sessions existantes.
     * Retourne les tokens (compte activé).
     *
     * @throws ResponseStatusException 400 si resetToken invalide ou expiré
     */
    AuthResponse setPasswordWithToken(ResetPasswordWithTokenRequest request);
}
