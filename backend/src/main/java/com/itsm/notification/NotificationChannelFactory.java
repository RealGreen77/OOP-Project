package com.itsm.notification;

import com.itsm.domain.notification.NotificationChannelType;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Provides the {@link NotificationChannel} implementation for a channel type.
 *
 * // [Pattern: Factory] Callers ask for a channel by type and never instantiate concrete channels.
 * // [Collections] EnumMap keyed by NotificationChannelType.
 */
@Component
public class NotificationChannelFactory {

    private final Map<NotificationChannelType, NotificationChannel> channels;

    public NotificationChannelFactory(List<NotificationChannel> implementations) {
        EnumMap<NotificationChannelType, NotificationChannel> map = new EnumMap<>(NotificationChannelType.class);
        implementations.forEach(c -> map.put(c.type(), c));
        this.channels = Collections.unmodifiableMap(map);
    }

    public NotificationChannel get(NotificationChannelType type) {
        NotificationChannel channel = channels.get(type);
        if (channel == null) throw new IllegalArgumentException("No notification channel registered for " + type);
        return channel;
    }

    public Collection<NotificationChannel> all() {
        return channels.values();
    }
}
