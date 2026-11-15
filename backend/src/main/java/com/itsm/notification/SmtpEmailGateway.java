package com.itsm.notification;

import com.itsm.config.ItsmProperties;
import com.itsm.domain.notification.DeliveryStatus;
import com.itsm.exception.ExternalServiceException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

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

    public SmtpEmailGateway(JavaMailSender mailSender, ItsmProperties properties) {
        this.mailSender = mailSender;
        this.from = properties.notifications().fromAddress();
    }

    @Override
    public DeliveryStatus send(String to, String subject, String body) {
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
}
