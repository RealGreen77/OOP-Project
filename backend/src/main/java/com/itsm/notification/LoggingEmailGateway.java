package com.itsm.notification;

import com.itsm.domain.notification.DeliveryStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Simulated e-mail provider: writes the e-mail to the log. Used when no SMTP server is configured. */
@Component
@ConditionalOnProperty(prefix = "itsm.notifications", name = "email-mode", havingValue = "log", matchIfMissing = true)
public class LoggingEmailGateway implements EmailGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailGateway.class);

    @Override
    public DeliveryStatus send(String to, String subject, String body) {
        log.info("[SIMULATED EMAIL] to={} subject=\"{}\"", to, subject);
        return DeliveryStatus.SIMULATED;
    }
}
