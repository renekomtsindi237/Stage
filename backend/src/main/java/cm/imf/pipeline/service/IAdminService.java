package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.CreateAgenceRequest;
import cm.imf.pipeline.dto.request.CreateUserRequest;
import cm.imf.pipeline.dto.response.AgenceResponse;
import cm.imf.pipeline.dto.response.ImfResponse;
import cm.imf.pipeline.dto.response.UserResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

/**
 * Contrat du service d'administration — réservé au rôle DSI.
 * Gère le cycle de vie des comptes utilisateurs dans le périmètre de son IMF.
 * Toutes les opérations sont strictement scopées à l'IMF du DSI connecté.
 */
public interface IAdminService {

    /** Retourne les informations de l'IMF du DSI connecté (lecture seule). */
    ImfResponse getImfInfo();

    /** Liste paginée des utilisateurs de l'IMF, triée par username. */
    Page<UserResponse> listUsers(int page, int size);

    /** Détail d'un utilisateur de l'IMF par ID. */
    UserResponse getById(UUID uid);

    /**
     * Crée un nouvel utilisateur dans l'IMF du DSI.
     * Rôles autorisés : DIRECTEUR, RESPONSABLE_RECOUVREMENT, ANALYSTE, AGENT.
     * Rôles interdits : DSI, SUPER_ADMIN (prérogative de la plateforme).
     */
    UserResponse createUser(CreateUserRequest request);

    /** Désactive un compte utilisateur (actif → inactif). */
    UserResponse deactivate(UUID uid);

    /** Réactive un compte utilisateur (inactif → actif). */
    UserResponse activate(UUID uid);

    /** Réinitialise le mot de passe d'un utilisateur avec le mot de passe fourni par le DSI. */
    void resetPassword(UUID uid, String newPassword);

    /** Liste des agences de l'IMF (pour le sélecteur de zone et la page agences). */
    java.util.List<AgenceResponse> listAgences();

    /** Liste des noms d'agences (pour l'autocomplete du sélecteur de zone). */
    java.util.List<String> listAgenceNoms();

    /** Crée une agence dans l'IMF du DSI. */
    AgenceResponse createAgence(CreateAgenceRequest request);

    /** Active ou désactive une agence. */
    AgenceResponse toggleAgence(UUID uid);

    /** Supprime une agence (seulement si aucun utilisateur ne lui est affecté). */
    void deleteAgence(UUID uid);

    /**
     * Upload ou remplacement de l'avatar d'un utilisateur de l'IMF.
     * Vérifie que le targetId appartient bien à l'IMF du DSI connecté.
     */
    UserResponse uploadUserAvatar(UUID targetUid, org.springframework.web.multipart.MultipartFile file);

    /**
     * Supprime l'avatar d'un utilisateur de l'IMF.
     */
    UserResponse removeUserAvatar(UUID targetUid);

    /**
     * Upload ou remplacement du logo de l'IMF du DSI connecté.
     */
    cm.imf.pipeline.dto.response.ImfResponse uploadImfLogo(org.springframework.web.multipart.MultipartFile file);
}
