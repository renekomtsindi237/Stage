package cm.imf.pipeline.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@imf.cm}")
    private String fromAddress;

    @Async("asyncExecutor")
    public void sendOtpEmail(String toEmail, String username, String code) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("IMF Pipeline — Code de réinitialisation");
            helper.setText(buildOtpHtml(username, code), true);

            mailSender.send(message);
            log.info("Email OTP envoyé à {}", toEmail);
        } catch (Exception e) {
            log.error("Échec envoi email OTP à {} : {}", toEmail, e.getMessage());
        }
    }

    private String buildOtpHtml(String username, String code) {
        return """
                <!DOCTYPE html>
                <html lang="fr">
                <body style="font-family:Arial,sans-serif;background:#f4f4f4;margin:0;padding:20px">
                  <div style="max-width:480px;margin:auto;background:#fff;border-radius:8px;padding:32px">
                    <h2 style="color:#1a73e8;margin-top:0">IMF Pipeline</h2>
                    <p>Bonjour <strong>%s</strong>,</p>
                    <p>Voici votre code de réinitialisation de mot de passe :</p>
                    <div style="text-align:center;margin:24px 0">
                      <span style="font-size:36px;font-weight:bold;letter-spacing:8px;
                                   color:#1a73e8;background:#eef4ff;padding:12px 24px;
                                   border-radius:6px;display:inline-block">%s</span>
                    </div>
                    <p style="color:#666;font-size:14px">
                      Ce code est valable <strong>10 minutes</strong> et ne peut être utilisé qu'une seule fois.<br>
                      Si vous n'avez pas demandé cette réinitialisation, ignorez cet email.
                    </p>
                    <hr style="border:none;border-top:1px solid #eee;margin:24px 0">
                    <p style="color:#999;font-size:12px;margin:0">
                      Ne partagez jamais ce code. IMF Pipeline ne vous demandera jamais votre mot de passe par email.
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(username, code);
    }
}
