package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.ChangePasswordRequest;
import cm.imf.pipeline.dto.request.UpdatePreferencesRequest;
import cm.imf.pipeline.dto.response.UserResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Contrat du service utilisateur (profil, tokens, actions self-service).
 */
public interface IUserService {

    /**
     * Détail d'un utilisateur par ID.
     *
     * @throws cm.imf.pipeline.exception.ResourceNotFoundException si non trouvé
     */
    UserResponse getById(Long id);

    /**
     * Détail d'un utilisateur par username.
     *
     * @throws cm.imf.pipeline.exception.ResourceNotFoundException si non trouvé
     */
    UserResponse getByUsername(String username);

    /**
     * Enregistre ou met à jour le token FCM d'un utilisateur connecté.
     */
    void updateFcmToken(User user, String token);

    /**
     * Désactive un compte utilisateur.
     *
     * @throws cm.imf.pipeline.exception.ResourceNotFoundException si non trouvé
     */
    UserResponse deactivate(Long id);

    /**
     * Récupère les utilisateurs d'une zone par rôle (pour ciblage FCM).
     */
    List<UserResponse> listByZoneAndRole(String zoneId, Role role);

    /**
     * Changement de mot de passe self-service.
     * Vérifie que currentPassword correspond au hash stocké avant d'appliquer newPassword.
     *
     * @throws cm.imf.pipeline.exception.BadRequestException si le mot de passe actuel est incorrect
     */
    void changePassword(User user, ChangePasswordRequest request);

    /**
     * Mise à jour partielle des préférences de l'utilisateur connecté.
     * Seuls les champs non-null dans la requête sont appliqués.
     */
    UserResponse updatePreferences(User user, UpdatePreferencesRequest request);

    /**
     * Upload et mise à jour de l'avatar de l'utilisateur connecté.
     * Accepte JPEG/PNG/WEBP, taille max configurable via app.upload.max-size-mb.
     * L'ancienne image est supprimée si elle existe sur le disque.
     *
     * @return URL publique de la nouvelle image (/api/uploads/avatars/{uuid}.{ext})
     */
    UserResponse uploadAvatar(User user, MultipartFile file);

    /**
     * Suppression de l'avatar — remet à null (l'UI affiche l'image par défaut).
     */
    UserResponse removeAvatar(User user);

    /**
     * Sert les octets de l'avatar (fichier local ou image par défaut).
     * Ne lève jamais : fallback PNG si l'utilisateur est anonyme ou le fichier manque.
     */
    org.springframework.http.ResponseEntity<byte[]> serveAvatar(User user);

    /**
     * Upload d'avatar pour un utilisateur cible par un administrateur (DSI/SUPER_ADMIN).
     * La vérification de scope (même IMF) est à la charge de l'appelant.
     */
    UserResponse uploadAvatarForUser(Long targetId, MultipartFile file);

    /**
     * Supprime l'avatar d'un utilisateur cible (action admin).
     */
    UserResponse removeAvatarForUser(Long targetId);

    /**
     * Nombre d'utilisateurs actuellement en ligne (toutes IMF).
     */
    long countOnline();

    /**
     * Nombre d'utilisateurs en ligne dans une IMF donnée.
     */
    long countOnlineByImf(Long imfId);
}
