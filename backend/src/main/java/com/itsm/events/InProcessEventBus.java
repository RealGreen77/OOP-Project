package com.itsm.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Synchronous in-process event bus. Listeners run inside the caller's transaction, so audit records
 * commit atomically with the change that caused them.
 *
 * // [Pattern: Observer] Concrete subject. Could be swapped for a Kafka/RabbitMQ implementation of
 * //                    {@link IncidentEventPublisher} to move to an event-driven microservice architecture.
 */
@Component
public class InProcessEventBus implements IncidentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InProcessEventBus.class);

    private final List<IncidentEventListener> listeners;

    public InProcessEventBus(List<IncidentEventListener> listeners) {
        this.listeners = listeners.stream().sorted(Comparator.comparingInt(IncidentEventListener::order)).toList();
    }

    @Override
    public void publish(IncidentEvent event) {
        for (IncidentEventListener listener : listeners) {
            if (listener.critical()) {
                listener.onEvent(event);
            } else {
                try {
                    listener.onEvent(event);
                } catch (RuntimeException ex) {
                    log.warn("Listener {} failed for {} on {}: {}", listener.getClass().getSimpleName(),
                            event.eventType(), event.incident().getNumber(), ex.getMessage(), ex);
                }
            }
        }
    }

    public List<IncidentEventListener> listeners() {
        return listeners;
    }
}
