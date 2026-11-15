package com.itsm.events;

/**
 * Subject side of the Observer pattern.
 * // [Pattern: Observer] [OOP: Interfaces] Services depend on this abstraction, not on concrete listeners.
 */
public interface IncidentEventPublisher {

    void publish(IncidentEvent event);
}
