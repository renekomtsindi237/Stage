package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.LoginRequest;
import cm.imf.pipeline.dto.request.RefreshRequest;
import cm.imf.pipeline.dto.request.VerifyOtpRequest;
import cm.imf.pipeline.dto.response.AuthResponse;
import cm.imf.pipeline.dto.response.OtpInitResponse;
import cm.imf.pipeline.dto.response.OtpVerifyResponse;

public interface IAuthService {

    /** Login SUPER_ADMIN uniquement (email + password). Retourne 403 pour les autres rôles. */
    AuthResponse login(LoginRequest request);

    AuthResponse refresh(RefreshRequest request);

    void logout(String refreshToken);

    /**
     * Étape 1 — Envoie un code OTP par email (1ère connexion ou Nième — flux identique).
     * Retourne toujours le même message générique (anti-énumération).
     */
    OtpInitResponse requestOtp(String email);

    /**
     * Étape 2 — Vérifie le code OTP. Retourne toujours AUTHENTICATED + tokens JWT.
     * Active le compte automatiquement à la 1ère connexion (mustChangePassword → false).
     *
     * @throws ResponseStatusException 400 si code invalide, expiré ou épuisé (3 tentatives)
     */
    OtpVerifyResponse verifyOtp(VerifyOtpRequest request);
}
