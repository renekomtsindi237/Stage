package cm.imf.pipeline.service;

import cm.imf.pipeline.entity.AlerteImpaye;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.repository.AlerteRepository;
import cm.imf.pipeline.repository.UserRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService implements INotificationService {

    private final UserRepository userRepository;
    private final AlerteRepository alerteRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${firebase.enabled:true}")
    private boolean firebaseEnabled;

    /**
     * Envoie un push FCM à tous les utilisateurs d'un rôle ayant un fcmToken.
     */
    @Async
    public void sendPushToRole(Role role, String title, String body) {
        if (!firebaseEnabled) {
            log.info("[FCM désactivé] Push simulé — role: {} | titre: {}", role, title);
            return;
        }
        List<User> targets = userRepository.findByRoleAndFcmTokenIsNotNull(role);
        for (User user : targets) {
            sendPushToToken(user.getFcmToken(), title, body);
        }
    }

    /**
     * Envoie un push FCM à un token spécifique.
     */
    @Async
    public void sendPushToToken(String fcmToken, String title, String body) {
        if (!firebaseEnabled || fcmToken == null) return;
        try {
            Message msg = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();
            String response = FirebaseMessaging.getInstance().send(msg);
            log.debug("FCM envoyé — messageId: {}", response);
        } catch (Exception e) {
            log.error("Échec FCM vers token {} : {}", fcmToken, e.getMessage());
        }
    }

    /**
     * Envoie un email SMTP.
     */
    @Async
    public void sendEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Email envoyé à {} | sujet: {}", to, subject);
        } catch (Exception e) {
            log.error("Échec email vers {} : {}", to, e.getMessage());
        }
    }

    /**
     * Enregistre le token FCM d'un utilisateur.
     */
    public void registerFcmToken(Long userId, String token) {
        userRepository.updateFcmToken(userId, token);
        log.info("FCM token mis à jour pour l'utilisateur id={}", userId);
    }

    /**
     * Notifie les responsables recouvrement d'une alerte impayé.
     * Appelée par le pipeline via POST /internal/alertes/notify
     */
    @Async
    public void notifierAlerteImpaye(Long alerteId) {
        alerteRepository.findById(alerteId).ifPresent(alerte -> {
            String titre = "Alerte impayé — " + alerte.getJoursRetard() + " jours";
            String corps = String.format(
                    "Prêt %s en retard de %d jours.\nMontant en retard : %,.0f XAF.",
                    alerte.getIdPret(), alerte.getJoursRetard(),
                    alerte.getMontantEnRetard().doubleValue());

            sendPushToRole(Role.RESPONSABLE_RECOUVREMENT, titre, corps);
        });
    }
}
