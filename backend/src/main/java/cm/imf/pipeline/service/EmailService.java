package cm.imf.pipeline.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Service d'envoi d'emails transactionnels IMF Pipeline.
 *
 * Tous les envois sont asynchrones (@Async) pour ne pas bloquer les requêtes HTTP.
 * Les erreurs d'envoi sont loggées sans être propagées (best-effort).
 *
 * Le logo MicroRecouv est intégré en CID inline (MIME multipart/related) —
 * rendu fiable dans tous les clients mail sans dépendance externe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender  mailSender;
    private final ResourceLoader  resourceLoader;

    /** Adresse technique Gmail (authentification SMTP). */
    @Value("${imf.mail.from:${spring.mail.username:noreply@imf.cm}}")
    private String fromAddress;

    /** Nom affiché dans le champ "De :" — ex: MicroRecouv, ServantAssist… */
    @Value("${imf.mail.from-name:MicroRecouv}")
    private String fromName;

    /** Reply-To : adresse Cloudflare redirigée (ex: contact@rene.it.com). Vide = pas de Reply-To. */
    @Value("${imf.mail.reply-to:}")
    private String replyTo;

    @Value("${imf.app.url:http://localhost:4200}")
    private String appUrl;

    @Value("${imf.app.name:IMF Pipeline}")
    private String appName;

    private static final String LOGO_CID      = "microrecouv-logo";
    private static final String LOGO_CLASSPATH = "classpath:email/logo.png";

    // ══════════════════════════════════════════════════════════════════════════
    // AUTHENTIFICATION
    // ══════════════════════════════════════════════════════════════════════════

    /** Étape OTP : code de vérification à 6 chiffres. */
    @Async("asyncExecutor")
    public void sendOtpEmail(String to, String username, String code) {
        String subject = appName + " — Code de vérification";
        String content = block("Vérification de votre identité",
                """
                <p style="margin:0 0 16px">Bonjour <strong>%s</strong>,</p>
                <p style="margin:0 0 24px">Voici votre code de vérification à usage unique :</p>
                """.formatted(username)
                + codeBlock(code)
                + """
                <p style="margin:16px 0 0;color:#555;font-size:14px">
                  Ce code est valable <strong>10 minutes</strong> et ne peut être utilisé qu'une seule fois.<br>
                  Si vous n'avez pas demandé ce code, ignorez cet email — votre compte reste sécurisé.
                </p>
                """,
                "warning");
        send(to, subject, layout(content, subject));
    }

    /** Compte créé par un administrateur — invitation à activer le compte. */
    @Async("asyncExecutor")
    public void sendWelcomeEmail(String to, String username, String roleName, String imfName) {
        String subject = "Bienvenue sur " + appName;
        String roleLabel = formatRole(roleName);
        String content = block("Votre compte a été créé",
                """
                <p style="margin:0 0 16px">Bonjour <strong>%s</strong>,</p>
                <p style="margin:0 0 16px">
                  Un compte <strong>%s</strong> vous a été créé sur la plateforme <strong>%s</strong>
                  %s.
                </p>
                <p style="margin:0 0 24px">
                  Pour activer votre compte et définir votre mot de passe, cliquez sur le bouton ci-dessous.
                  Vous recevrez un code de vérification par email.
                </p>
                """.formatted(username, roleLabel, appName,
                        imfName != null ? "pour <strong>" + imfName + "</strong>" : "")
                + button("Activer mon compte", appUrl + "/auth/activate")
                + infoBox("Votre adresse email est votre identifiant de connexion.", "info"),
                "success");
        send(to, subject, layout(content, subject));
    }

    /** Mot de passe modifié — notification de sécurité. */
    @Async("asyncExecutor")
    public void sendPasswordChangedEmail(String to, String username) {
        String subject = appName + " — Mot de passe modifié";
        String content = block("Votre mot de passe a été modifié",
                """
                <p style="margin:0 0 16px">Bonjour <strong>%s</strong>,</p>
                <p style="margin:0 0 24px">
                  Le mot de passe de votre compte a été modifié avec succès le <strong>%s</strong>.
                </p>
                """.formatted(username, today())
                + infoBox(
                        "Si vous n'êtes pas à l'origine de cette modification, contactez immédiatement votre administrateur et changez votre mot de passe.",
                        "warning"),
                "warning");
        send(to, subject, layout(content, subject));
    }

    /** Nouvelle connexion détectée — alerte de sécurité. */
    @Async("asyncExecutor")
    public void sendLoginAlertEmail(String to, String username, String ipAddress) {
        String subject = appName + " — Nouvelle connexion détectée";
        String content = block("Nouvelle connexion à votre compte",
                """
                <p style="margin:0 0 16px">Bonjour <strong>%s</strong>,</p>
                <p style="margin:0 0 24px">Une connexion à votre compte vient d'être détectée.</p>
                """.formatted(username)
                + metaTable(new String[][]{
                        {"Date", today()},
                        {"Adresse IP", ipAddress != null ? ipAddress : "N/A"}
                })
                + infoBox(
                        "Si vous n'êtes pas à l'origine de cette connexion, changez immédiatement votre mot de passe et alertez votre DSI.",
                        "danger"),
                "warning");
        send(to, subject, layout(content, subject));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RGPD — LOI 2024/017 CAMEROUN
    // ══════════════════════════════════════════════════════════════════════════

    /** Confirmation de réception d'une demande RGPD (art. 37-43). */
    @Async("asyncExecutor")
    public void sendRgpdConfirmation(String to, String username, String typedemande,
                                      String reference, LocalDate deadline) {
        String subject = appName + " — Demande RGPD reçue · " + reference;
        String content = block("Votre demande RGPD a bien été reçue",
                """
                <p style="margin:0 0 16px">Bonjour <strong>%s</strong>,</p>
                <p style="margin:0 0 24px">
                  Nous avons bien reçu votre demande d'exercice de droit <strong>%s</strong>
                  conformément à la Loi camerounaise n° 2024/017.
                </p>
                """.formatted(username, typedemande)
                + metaTable(new String[][]{
                        {"Référence", reference},
                        {"Type de demande", typedemande},
                        {"Date de réception", today()},
                        {"Date limite de réponse", deadline != null ? formatDate(deadline) : "30 jours"}
                })
                + infoBox(
                        "Nous disposons de 30 jours pour traiter votre demande (art. 41 Loi 2024/017). "
                        + "Vous serez notifié par email dès qu'une décision sera prise.",
                        "info"),
                "info");
        send(to, subject, layout(content, subject));
    }

    /** Notification de traitement d'une demande RGPD. */
    @Async("asyncExecutor")
    public void sendRgpdProcessed(String to, String username, String typedemande,
                                   String reference, String decision, String message) {
        boolean accepted = "ACCEPTEE".equalsIgnoreCase(decision) || "TRAITEE".equalsIgnoreCase(decision);
        String subject = appName + " — Demande RGPD traitée · " + reference;
        String content = block("Votre demande RGPD a été traitée",
                """
                <p style="margin:0 0 16px">Bonjour <strong>%s</strong>,</p>
                <p style="margin:0 0 24px">
                  Votre demande <strong>%s</strong> (référence : %s) a été traitée.
                </p>
                """.formatted(username, typedemande, reference)
                + metaTable(new String[][]{
                        {"Référence", reference},
                        {"Décision", decision},
                        {"Date de traitement", today()}
                })
                + (message != null && !message.isBlank()
                        ? "<p style=\"margin:16px 0 0\"><strong>Message :</strong> " + escapeHtml(message) + "</p>"
                        : ""),
                accepted ? "success" : "warning");
        send(to, subject, layout(content, subject));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ALERTES & VIOLATIONS
    // ══════════════════════════════════════════════════════════════════════════

    /** Alerte critique déclenchée sur un dossier client. */
    @Async("asyncExecutor")
    public void sendCriticalAlertEmail(String to, String username, String alertType,
                                        String clientNom, String details) {
        String subject = appName + " — Alerte critique : " + alertType;
        String content = block("Alerte critique détectée",
                """
                <p style="margin:0 0 16px">Bonjour <strong>%s</strong>,</p>
                <p style="margin:0 0 24px">
                  Une alerte de type <strong>%s</strong> a été déclenchée sur le dossier suivant.
                </p>
                """.formatted(username, alertType)
                + metaTable(new String[][]{
                        {"Type d'alerte", alertType},
                        {"Client", clientNom != null ? clientNom : "N/A"},
                        {"Détails", details != null ? details : "—"},
                        {"Date", today()}
                })
                + button("Voir l'alerte", appUrl + "/alertes")
                + infoBox("Traitez cette alerte rapidement pour éviter une escalade.", "danger"),
                "danger");
        send(to, subject, layout(content, subject));
    }

    /** Alerte de délai 72h pour violation de données (art. 22 §1 Loi 2024/017). */
    @Async("asyncExecutor")
    public void sendViolation72hAlert(String to, String imfNom, String reference,
                                       long heuresEcoulees) {
        boolean depasse = heuresEcoulees >= 72;
        String subject = appName + " — " + (depasse ? "URGENT : délai 72h dépassé" : "Violation de données — délai 72h")
                + " · " + reference;
        String content = block(
                depasse ? "Délai légal de 72h dépassé" : "Délai de notification approche",
                """
                <p style="margin:0 0 16px">
                  La violation de données <strong>%s</strong> (%s) nécessite une notification
                  à l'autorité de protection des données.
                </p>
                """.formatted(reference, imfNom != null ? imfNom : "IMF")
                + metaTable(new String[][]{
                        {"Référence", reference},
                        {"Heures écoulées", heuresEcoulees + "h / 72h"},
                        {"Statut", depasse ? "⚠ DÉLAI DÉPASSÉ" : "Action requise dans les prochaines heures"},
                        {"Base légale", "Art. 22 §1 Loi 2024/017 Cameroun"}
                })
                + button("Gérer la violation", appUrl + "/admin/violations")
                + infoBox(
                        "La non-notification dans le délai de 72h peut entraîner des sanctions (art. 22 Loi 2024/017).",
                        "danger"),
                "danger");
        send(to, subject, layout(content, subject));
    }

    /** Notification à un utilisateur qu'il a été désactivé ou réactivé. */
    @Async("asyncExecutor")
    public void sendAccountStatusEmail(String to, String username, boolean activated) {
        String action = activated ? "réactivé" : "désactivé";
        String subject = appName + " — Compte " + action;
        String content = block("Votre compte a été " + action,
                """
                <p style="margin:0 0 16px">Bonjour <strong>%s</strong>,</p>
                <p style="margin:0 0 24px">
                  Votre compte sur la plateforme <strong>%s</strong> a été <strong>%s</strong>
                  le <strong>%s</strong> par votre administrateur.
                </p>
                """.formatted(username, appName, action, today())
                + (activated
                        ? button("Accéder à la plateforme", appUrl + "/auth/login")
                        : infoBox("Pour toute question, contactez votre administrateur.", "info")),
                activated ? "success" : "warning");
        send(to, subject, layout(content, subject));
    }

    /** Résumé d'activité mensuel envoyé aux directeurs/DSI. */
    @Async("asyncExecutor")
    public void sendActivitySummaryEmail(String to, String username, String imfNom,
                                          int totalDossiers, int alertesCritiques,
                                          int demandesRgpd, String mois) {
        String subject = appName + " — Résumé d'activité " + mois + " · " + imfNom;
        String content = block("Résumé d'activité — " + mois,
                """
                <p style="margin:0 0 16px">Bonjour <strong>%s</strong>,</p>
                <p style="margin:0 0 24px">
                  Voici le résumé d'activité de <strong>%s</strong> pour le mois de <strong>%s</strong>.
                </p>
                """.formatted(username, imfNom, mois)
                + metaTable(new String[][]{
                        {"Dossiers actifs", String.valueOf(totalDossiers)},
                        {"Alertes critiques", String.valueOf(alertesCritiques)},
                        {"Demandes RGPD en cours", String.valueOf(demandesRgpd)},
                        {"Période", mois}
                })
                + button("Voir le tableau de bord", appUrl + "/dashboard"),
                "info");
        send(to, subject, layout(content, subject));
    }

    /**
     * Email envoyé au nouvel utilisateur créé par le DSI.
     * Explique exactement comment se connecter via le flux email + OTP.
     */
    @Async("asyncExecutor")
    public void sendNouvelUtilisateurEmail(String to, String username,
                                            String roleName, String imfName) {
        String subject = "Votre compte " + appName + " — Informations de connexion";
        String roleLabel = formatRole(roleName);
        String content = block("Votre compte a été créé",
                """
                <p style="margin:0 0 16px">Bonjour <strong>%s</strong>,</p>
                <p style="margin:0 0 16px">
                  Un compte <strong>%s</strong> vous a été créé sur la plateforme
                  <strong>%s</strong>%s.
                </p>
                <p style="margin:0 0 8px">
                  <strong>Votre identifiant de connexion est votre adresse email :</strong>
                </p>
                """.formatted(
                        username, roleLabel, appName,
                        imfName != null && !imfName.isBlank()
                            ? " pour <strong>" + escapeHtml(imfName) + "</strong>"
                            : "")
                + codeBlock(to)
                + """
                <h3 style="margin:24px 0 12px;color:#1b2f4b;font-size:15px">
                  Comment vous connecter ?
                </h3>
                """
                + metaTable(new String[][]{
                        {"Étape 1", "Accédez à la plateforme en cliquant sur le bouton ci-dessous"},
                        {"Étape 2", "Saisissez votre adresse email : " + to},
                        {"Étape 3", "Cliquez sur « Recevoir mon code »"},
                        {"Étape 4", "Consultez votre boîte mail — vous recevrez un code OTP à 6 chiffres"},
                        {"Étape 5", "Saisissez ce code sur la page de vérification"},
                        {"Validité du code", "10 minutes — demandez-en un nouveau si expiré"}
                })
                + button("Accéder à la plateforme", appUrl + "/login")
                + infoBox(
                        "Ce système utilise une authentification sans mot de passe (OTP). "
                        + "Chaque connexion génère un code unique envoyé à votre adresse email — "
                        + "vous n'avez aucun mot de passe à mémoriser.",
                        "info"),
                "success");
        send(to, subject, layout(content, subject));
    }

    /** Confirmation de réception d'un message de contact public (page de connexion). */
    @Async("asyncExecutor")
    public void sendContactSupportConfirmation(String to, String nomExpéditeur,
                                                String sujet, String reference) {
        String subject = appName + " — Votre message a bien été reçu";
        String content = block("Votre message a été transmis",
                """
                <p style="margin:0 0 16px">Bonjour <strong>%s</strong>,</p>
                <p style="margin:0 0 24px">
                  Nous avons bien reçu votre message concernant : <strong>%s</strong>.
                </p>
                """.formatted(escapeHtml(nomExpéditeur), escapeHtml(sujet))
                + metaTable(new String[][]{
                        {"Référence", reference},
                        {"Statut", "En attente de traitement"},
                        {"Date de réception", today()}
                })
                + infoBox(
                        "Notre équipe support vous répondra dans les meilleurs délais à cette adresse email.",
                        "info"),
                "info");
        send(to, subject, layout(content, subject));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TEST — diagnostic de configuration SMTP (synchrone, lève une exception)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Envoi de test synchrone (non-@Async) : lève une exception si la
     * configuration SMTP est incorrecte, permettant un retour immédiat dans
     * l'API sans attendre un log asynchrone.
     */
    public void sendTestEmail(String to) throws Exception {
        String subject = "[TEST] " + fromName + " — Configuration SMTP Gmail";
        String replyToDisplay = (replyTo != null && !replyTo.isBlank()) ? replyTo : "— (non configuré)";
        String content = block("Test de configuration SMTP",
                "<p style=\"margin:0 0 16px\">Cet email confirme que la configuration SMTP Gmail est correcte.</p>"
                + metaTable(new String[][]{
                        {"Serveur SMTP",  "smtp.gmail.com:587 (STARTTLS)"},
                        {"De (technique)", fromAddress},
                        {"Nom affiché",   fromName},
                        {"Reply-To",      replyToDisplay},
                        {"URL app",       appUrl},
                        {"Date",          today()}
                })
                + infoBox("Si vous recevez cet email et que le champ \"Répondre à\" pointe vers "
                        + replyToDisplay + ", la configuration est complète.", "success"),
                "success");
        sendSync(to, subject, layout(content, subject));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INFRASTRUCTURE — envoi + layout HTML
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Envoie un email HTML en mode multipart/related.
     * Le logo MicroRecouv est embarqué en tant qu'image inline (CID)
     * pour un rendu fiable dans tous les clients mail.
     */
    private void send(String to, String subject, String html) {
        try {
            sendSync(to, subject, html);
        } catch (Exception e) {
            log.error("Échec envoi email → {} | {} : {}", to, subject, e.getMessage());
        }
    }

    /** Version synchrone qui propage l'exception — utilisée par sendTestEmail(). */
    private void sendSync(String to, String subject, String html) throws Exception {
        var message = mailSender.createMimeMessage();
        var helper  = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress, fromName);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);

        // Reply-To : adresse Cloudflare (contact@rene.it.com) si configurée
        if (replyTo != null && !replyTo.isBlank()) {
            helper.setReplyTo(replyTo);
        }

        Resource logo = resourceLoader.getResource(LOGO_CLASSPATH);
        if (logo.exists()) {
            helper.addInline(LOGO_CID, logo, "image/png");
        } else {
            log.warn("Logo email introuvable : {}", LOGO_CLASSPATH);
        }

        mailSender.send(message);
        log.info("Email envoyé → {} | {} [from-name={} reply-to={}]", to, subject, fromName,
                replyTo != null && !replyTo.isBlank() ? replyTo : "—");
    }

    // ── Composants HTML ───────────────────────────────────────────────────────

    /**
     * Layout principal de tous les emails.
     * L'en-tête affiche le logo MicroRecouv via src="cid:microrecouv-logo".
     */
    private String layout(String content, String previewText) {
        return """
                <!DOCTYPE html>
                <html lang="fr">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:#f0f2f5;font-family:Arial,Helvetica,sans-serif">
                  <!-- Preview text (masqué, affiché dans la liste de messages) -->
                  <div style="display:none;max-height:0;overflow:hidden;color:#f0f2f5">%s</div>

                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f0f2f5;padding:32px 0">
                    <tr><td align="center">
                      <table width="600" cellpadding="0" cellspacing="0"
                             style="max-width:600px;width:100%%">

                        <!-- En-tête — logo MicroRecouv embarqué (CID inline) -->
                        <tr>
                          <td style="background:#1565C0;border-radius:8px 8px 0 0;
                                     padding:20px 32px 16px;text-align:center">
                            <img src="cid:%s"
                                 alt="%s"
                                 style="display:block;margin:0 auto;
                                        max-height:72px;max-width:72px;
                                        width:auto;height:auto" />
                            <p style="margin:10px 0 0;color:rgba(255,255,255,0.75);
                                      font-size:12px;letter-spacing:0.3px">
                              Plateforme de gestion IMF — Cameroun
                            </p>
                          </td>
                        </tr>

                        <!-- Corps -->
                        <tr>
                          <td style="background:#ffffff;padding:32px;
                                     border-left:1px solid #e0e0e0;border-right:1px solid #e0e0e0">
                            %s
                          </td>
                        </tr>

                        <!-- Pied de page -->
                        <tr>
                          <td style="background:#f8f9fa;border:1px solid #e0e0e0;border-top:none;
                                     border-radius:0 0 8px 8px;padding:20px 32px;text-align:center">
                            <p style="margin:0;color:#9e9e9e;font-size:11px;line-height:1.6">
                              Cet email a été généré automatiquement par <strong>%s</strong>.<br>
                              Merci de ne pas répondre à cet email.<br>
                              © %s %s — Conforme Loi n° 2024/017 sur la protection des données personnelles au Cameroun
                            </p>
                          </td>
                        </tr>

                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(
                        escapeHtml(previewText),          // <title>
                        escapeHtml(previewText),          // preview text masqué
                        LOGO_CID,                         // cid:microrecouv-logo
                        escapeHtml(appName),              // alt du logo
                        content,                          // corps du message
                        escapeHtml(appName),              // footer "généré par"
                        java.time.Year.now().getValue(),  // année ©
                        escapeHtml(appName)               // footer copyright
                );
    }

    private String block(String title, String bodyHtml, String type) {
        String color = switch (type) {
            case "success" -> "#2E7D32";
            case "warning" -> "#E65100";
            case "danger"  -> "#C62828";
            default        -> "#1565C0";
        };
        return """
               <h2 style="margin:0 0 24px;color:%s;font-size:18px;font-weight:700;
                          border-bottom:2px solid %s;padding-bottom:12px">%s</h2>
               %s
               """.formatted(color, color, escapeHtml(title), bodyHtml);
    }

    private String codeBlock(String code) {
        return """
               <div style="text-align:center;margin:24px 0">
                 <span style="display:inline-block;font-size:38px;font-weight:700;
                              letter-spacing:10px;color:#1565C0;background:#EEF2FF;
                              padding:14px 28px;border-radius:8px;border:2px solid #C5CAE9">
                   %s
                 </span>
               </div>
               """.formatted(escapeHtml(code));
    }

    private String button(String label, String url) {
        return """
               <div style="text-align:center;margin:28px 0">
                 <a href="%s"
                    style="display:inline-block;background:#1565C0;color:#ffffff;
                           text-decoration:none;font-weight:700;font-size:15px;
                           padding:14px 32px;border-radius:6px;letter-spacing:0.3px">
                   %s →
                 </a>
               </div>
               """.formatted(url, escapeHtml(label));
    }

    private String infoBox(String message, String type) {
        String[] style = switch (type) {
            case "success" -> new String[]{"#E8F5E9", "#2E7D32", "#A5D6A7"};
            case "warning" -> new String[]{"#FFF3E0", "#E65100", "#FFCC80"};
            case "danger"  -> new String[]{"#FFEBEE", "#C62828", "#EF9A9A"};
            default        -> new String[]{"#E3F2FD", "#1565C0", "#90CAF9"};
        };
        String icon = switch (type) {
            case "success" -> "✓";
            case "warning" -> "⚠";
            case "danger"  -> "✕";
            default        -> "ℹ";
        };
        return """
               <div style="background:%s;border-left:4px solid %s;border-radius:4px;
                           padding:14px 16px;margin:20px 0">
                 <p style="margin:0;color:%s;font-size:13px;line-height:1.6">
                   <strong>%s</strong>&nbsp;&nbsp;%s
                 </p>
               </div>
               """.formatted(style[0], style[1], style[1], icon, escapeHtml(message));
    }

    private String metaTable(String[][] rows) {
        StringBuilder sb = new StringBuilder(
                "<table cellpadding=\"0\" cellspacing=\"0\" width=\"100%%\" "
                + "style=\"border-collapse:collapse;margin:16px 0 24px\">");
        for (String[] row : rows) {
            sb.append("""
                      <tr>
                        <td style="padding:10px 12px;background:#f5f5f5;border:1px solid #e0e0e0;
                                   font-size:13px;color:#616161;font-weight:700;width:40%%">%s</td>
                        <td style="padding:10px 12px;background:#ffffff;border:1px solid #e0e0e0;
                                   font-size:13px;color:#212121">%s</td>
                      </tr>
                      """.formatted(escapeHtml(row[0]), escapeHtml(row[1])));
        }
        sb.append("</table>");
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private String formatDate(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private String formatRole(String role) {
        if (role == null) return "Utilisateur";
        return switch (role.toUpperCase()) {
            case "SUPER_ADMIN"              -> "Super Administrateur";
            case "DIRECTEUR"                -> "Directeur";
            case "DSI"                      -> "Administrateur DSI";
            case "RESPONSABLE_RECOUVREMENT" -> "Responsable Recouvrement";
            case "ANALYSTE"                 -> "Analyste";
            case "AGENT"                    -> "Agent Terrain";
            default -> role;
        };
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
