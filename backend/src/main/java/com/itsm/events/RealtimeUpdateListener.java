package com.itsm.events;

import com.itsm.notification.RealtimePublisher;
import org.springframework.stereotype.Component;

/**
 * Broadcasts every incident change on the live feed so dashboards and incident lists refresh instantly.
 * // [Pattern: Observer] Third independent observer - added without modifying publishers or other listeners.
 */
@Component
public class RealtimeUpdateListener implements IncidentEventListener {

    private final RealtimePublisher realtime;

    public RealtimeUpdateListener(RealtimePublisher realtime) {
        this.realtime = realtime;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void onEvent(IncidentEvent event) {
        realtime.incidentChanged(event.incident().getId(), event.incident().getNumber(), event.eventType(),
                event.incident().getStatus().name());
    }
}
