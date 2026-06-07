package cm.imf.pipeline.service;

import cm.imf.pipeline.enums.Role;

/**
 * Contrat du service de notifications multicanal (FCM + Email).
 * Toutes les méthodes d'envoi sont asynchrones (@Async).
 */
public interface INotificationService {

    /**
     * Envoie un push FCM à tous les utilisateurs d'un rôle disposant d'un fcmToken.
     */
    void sendPushToRole(Role role, String title, String body);

    /**
     * Envoie un push FCM à un token spécifique.
     */
    void sendPushToToken(String fcmToken, String title, String body);

    /**
     * Envoie un email SMTP.
     */
    void sendEmail(String to, String subject, String text);

    /**
     * Enregistre le token FCM d'un utilisateur (appelé au login ou refresh).
     */
    void registerFcmToken(Long userId, String token);

    /**
     * Notifie les responsables recouvrement d'une alerte impayé.
     * Appelée par le pipeline via POST /internal/alertes/notify.
     */
    void notifierAlerteImpaye(Long alerteId);
}
