package com.itsm.notification;

import com.itsm.domain.notification.DeliveryStatus;
import com.itsm.domain.notification.Notification;
import com.itsm.domain.notification.NotificationChannelType;
import com.itsm.domain.user.User;
import com.itsm.repository.NotificationRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-app inbox + instant WebSocket push. Always enabled for every user.
 * // [OOP: Polymorphism] One of three interchangeable NotificationChannel implementations.
 */
@Component
public class InAppNotificationChannel implements NotificationChannel {

    private final NotificationRepository repository;
    private final RealtimePublisher realtime;
    private final Clock clock;

    public InAppNotificationChannel(NotificationRepository repository, RealtimePublisher realtime, Clock clock) {
        this.repository = repository;
        this.realtime = realtime;
        this.clock = clock;
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.IN_APP;
    }

    @Override
    public boolean supports(User recipient, NotificationMessage message) {
        return recipient.isActive();
    }

    @Override
    public String destinationFor(User recipient) {
        return null;
    }

    @Override
    public DeliveryStatus deliver(User recipient, NotificationMessage message) {
        Notification saved = repository.save(new Notification(recipient, type(), message.eventType(), message.title(),
                message.body(), message.incidentId(), message.incidentNumber(), null, clock.instant()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", saved.getId());
        payload.put("eventType", saved.getEventType());
        payload.put("title", saved.getTitle());
        payload.put("message", saved.getMessage());
        payload.put("incidentId", saved.getIncidentId());
        payload.put("incidentNumber", saved.getIncidentNumber());
        payload.put("createdAt", saved.getCreatedAt() == null ? clock.instant().toString() : saved.getCreatedAt().toString());
        realtime.toUser(recipient.getId(), payload);
        return DeliveryStatus.DELIVERED;
    }
}
