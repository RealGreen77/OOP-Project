package com.itsm.notification;

import com.itsm.domain.incident.Priority;

/** Channel-independent content of a notification. */
public record NotificationMessage(
        String eventType,
        String title,
        String body,
        Long incidentId,
        String incidentNumber,
        Priority priority) {

    /** Urgent messages (P1/P2 or SLA breaches) are eligible for SMS. */
    public boolean isUrgent() {
        return (priority != null && priority.isUrgent())
                || "SLA_BREACHED".equals(eventType)
                || "INCIDENT_ESCALATED".equals(eventType)
                || "MAJOR_INCIDENT_DETECTED".equals(eventType);
    }
}
