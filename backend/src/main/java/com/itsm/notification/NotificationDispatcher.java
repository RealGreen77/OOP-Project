package com.itsm.notification;

import com.itsm.domain.notification.DeliveryStatus;
import com.itsm.domain.notification.Notification;
import com.itsm.domain.notification.NotificationChannelType;
import com.itsm.domain.user.User;
import com.itsm.exception.ExternalServiceException;
import com.itsm.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fans a message out to every recipient over every channel that supports it.
 * External channel deliveries (EMAIL/SMS) are logged with their outcome; a failing provider is recorded as
 * FAILED and never aborts the business operation (graceful degradation on network failure).
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationChannelFactory channels;
    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationDispatcher(NotificationChannelFactory channels, NotificationRepository repository, Clock clock) {
        this.channels = channels;
        this.repository = repository;
        this.clock = clock;
    }

    public void dispatch(Collection<? extends User> recipients, NotificationMessage message) {
        // De-duplicate recipients by id while preserving order.
        Map<Long, User> unique = new LinkedHashMap<>();
        for (User u : recipients) {
            if (u != null && u.getId() != null) unique.putIfAbsent(u.getId(), u);
        }
        for (User recipient : unique.values()) {
            for (NotificationChannel channel : channels.all()) {
                if (!channel.supports(recipient, message)) continue;
                if (channel.type() == NotificationChannelType.IN_APP) {
                    channel.deliver(recipient, message);
                } else {
                    deliverExternal(channel, recipient, message);
                }
            }
        }
    }

    private void deliverExternal(NotificationChannel channel, User recipient, NotificationMessage message) {
        Notification logEntry = new Notification(recipient, channel.type(), message.eventType(), message.title(),
                message.body(), message.incidentId(), message.incidentNumber(), channel.destinationFor(recipient),
                clock.instant());
        try {
            DeliveryStatus status = channel.deliver(recipient, message);
            logEntry.markDelivered(status);
        } catch (ExternalServiceException ex) {
            log.warn("{} delivery to {} failed: {}", channel.type(), recipient.getUserCode(), ex.getMessage());
            logEntry.markFailed(ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Unexpected {} delivery failure to {}", channel.type(), recipient.getUserCode(), ex);
            logEntry.markFailed(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
        logEntry.markRead(clock.instant()); // log entries never appear in the unread inbox
        repository.save(logEntry);
    }
}
