package com.itsm.notification;

import com.itsm.config.ItsmProperties;
import com.itsm.domain.notification.DeliveryStatus;
import com.itsm.exception.ExternalServiceException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Adapts Spring's {@link JavaMailSender} (SMTP) to the {@link EmailGateway} interface.
 * In docker-compose the SMTP server is Mailpit, whose web UI shows every e-mail sent.
 *
 * // [Pattern: Adapter] Concrete adapter. Provider exceptions are translated to ExternalServiceException.
 */
@Component
@ConditionalOnProperty(prefix = "itsm.notifications", name = "email-mode", havingValue = "smtp")
public class SmtpEmailGateway implements EmailGateway {

    private final JavaMailSender mailSender;
    private final String from;
    private final List<String> simulatedDomains;

    public SmtpEmailGateway(JavaMailSender mailSender, ItsmProperties properties) {
        this.mailSender = mailSender;
        this.from = properties.notifications().fromAddress();
        List<String> domains = properties.notifications().simulatedEmailDomains();
        this.simulatedDomains = domains == null ? List.of()
                : domains.stream().map(d -> d.trim().toLowerCase(Locale.ROOT)).filter(d -> !d.isEmpty()).toList();
    }

    @Override
    public DeliveryStatus send(String to, String subject, String body) {
        if (isSimulated(to)) {
            return DeliveryStatus.SIMULATED; // demo-account domain: no real mailbox, so do not hit the provider
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            return DeliveryStatus.DELIVERED;
        } catch (MailException ex) {
            throw new ExternalServiceException("SMTP", ex.getMessage(), ex);
        }
    }

    /** True when the recipient's domain (or a parent domain) is on the configured simulated list. */
    private boolean isSimulated(String to) {
        if (to == null) return false;
        int at = to.lastIndexOf('@');
        if (at < 0) return false;
        String domain = to.substring(at + 1).trim().toLowerCase(Locale.ROOT);
        return simulatedDomains.stream().anyMatch(d -> domain.equals(d) || domain.endsWith("." + d));
    }
}
