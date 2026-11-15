package com.itsm.notification;

import com.itsm.config.ItsmProperties;
import com.itsm.domain.notification.DeliveryStatus;
import com.itsm.domain.notification.NotificationChannelType;
import com.itsm.domain.user.User;
import org.springframework.stereotype.Component;

/** E-mail channel; honours the recipient's e-mail preference. Delivery delegated to an {@link EmailGateway} adapter. */
@Component
public class EmailNotificationChannel implements NotificationChannel {

    private final EmailGateway gateway;
    private final String frontendBaseUrl;

    public EmailNotificationChannel(EmailGateway gateway, ItsmProperties properties) {
        this.gateway = gateway;
        this.frontendBaseUrl = properties.notifications().frontendBaseUrl();
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public boolean supports(User recipient, NotificationMessage message) {
        return recipient.isActive() && recipient.getPreferences().isEmailEnabled() && recipient.getEmail() != null;
    }

    @Override
    public String destinationFor(User recipient) {
        return recipient.getEmail();
    }

    @Override
    public DeliveryStatus deliver(User recipient, NotificationMessage message) {
        String subject = "[I-ITSM]" + (message.incidentNumber() == null ? "" : " " + message.incidentNumber())
                + " " + message.title();
        StringBuilder body = new StringBuilder()
                .append("Hello ").append(recipient.getFullName()).append(",\n\n")
                .append(message.body()).append("\n\n");
        if (message.incidentId() != null) {
            body.append("View the incident: ").append(frontendBaseUrl).append("/incidents/").append(message.incidentId()).append("\n\n");
        }
        body.append("-- I-ITSM Service Desk (automated message)");
        return gateway.send(recipient.getEmail(), subject, body.toString());
    }
}
