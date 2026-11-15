package com.itsm.notification;

import com.itsm.config.ItsmProperties;
import com.itsm.domain.notification.DeliveryStatus;
import com.itsm.domain.notification.NotificationChannelType;
import com.itsm.domain.user.User;
import org.springframework.stereotype.Component;

/**
 * SMS channel reserved for urgent messages (P1/P2, SLA breaches, escalations) to opted-in users with a phone.
 * Delivery delegated to an {@link SmsGateway} adapter.
 */
@Component
public class SmsNotificationChannel implements NotificationChannel {

    private final SmsGateway gateway;
    private final boolean enabled;

    public SmsNotificationChannel(SmsGateway gateway, ItsmProperties properties) {
        this.gateway = gateway;
        this.enabled = properties.notifications().smsEnabled();
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.SMS;
    }

    @Override
    public boolean supports(User recipient, NotificationMessage message) {
        return enabled && recipient.isActive() && message.isUrgent()
                && recipient.getPreferences().isSmsEnabled()
                && recipient.getPhone() != null && !recipient.getPhone().isBlank();
    }

    @Override
    public String destinationFor(User recipient) {
        return recipient.getPhone();
    }

    @Override
    public DeliveryStatus deliver(User recipient, NotificationMessage message) {
        String text = "I-ITSM " + (message.incidentNumber() == null ? "" : message.incidentNumber() + ": ") + message.title();
        return gateway.sendSms(recipient.getPhone(), text);
    }
}
