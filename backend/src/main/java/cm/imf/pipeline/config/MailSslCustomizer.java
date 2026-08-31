package cm.imf.pipeline.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Aligne JavaMail sur le port SMTP réel : SSL implicite (465) ou STARTTLS (587),
 * et allonge les timeouts pour les liaisons lentes.
 */
@Slf4j
@Component
public class MailSslCustomizer implements BeanPostProcessor {

    private static final String TIMEOUT_MS = "20000";

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof JavaMailSenderImpl sender)) {
            return bean;
        }

        int port = sender.getPort() > 0 ? sender.getPort() : 587;
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        props.put("mail.smtp.timeout", TIMEOUT_MS);
        props.put("mail.smtp.writetimeout", TIMEOUT_MS);

        String mode = "inchangé";
        if (port == 465) {
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.trust", sender.getHost());
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.starttls.required", "false");
            mode = "SSL/465";
        } else if (port == 587) {
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.ssl.enable", "false");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            mode = "STARTTLS/587";
        }

        String user = sender.getUsername();
        boolean passwordSet = sender.getPassword() != null && !sender.getPassword().isBlank();
        log.info("SMTP prêt host={} port={} user={} password={} mode={}",
                sender.getHost(),
                port,
                user != null && !user.isBlank() ? user : "(vide)",
                passwordSet ? "oui" : "non",
                mode);
        return bean;
    }
}
