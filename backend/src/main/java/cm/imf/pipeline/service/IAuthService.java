package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.LoginRequest;
import cm.imf.pipeline.dto.request.RefreshRequest;
import cm.imf.pipeline.dto.response.AuthResponse;

/**
 * Contrat du service d'authentification JWT.
 * Gère login, refresh de token et déconnexion.
 */
public interface IAuthService {

    /**
     * Authentifie un utilisateur et retourne un accessToken + refreshToken.
     *
     * @throws org.springframework.security.authentication.BadCredentialsException si les identifiants sont invalides
     * @throws org.springframework.security.authentication.DisabledException si le compte est désactivé
     */
    AuthResponse login(LoginRequest request);

    /**
     * Génère un nouveau accessToken à partir d'un refreshToken valide.
     *
     * @throws IllegalArgumentException si le refreshToken est invalide ou expiré
     */
    AuthResponse refresh(RefreshRequest request);

    /**
     * Invalide le refreshToken côté serveur (déconnexion).
     */
    void logout(String refreshToken);
}
