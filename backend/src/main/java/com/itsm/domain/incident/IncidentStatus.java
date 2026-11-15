package com.itsm.domain.incident;

import java.util.EnumSet;
import java.util.Set;

/**
 * Persisted lifecycle status. Behaviour for each status lives in the matching
 * {@link com.itsm.domain.incident.state.IncidentState} class (State pattern).
 */
public enum IncidentStatus {
    NEW,
    CLASSIFIED,
    ASSIGNED,
    ACKNOWLEDGED,
    IN_PROGRESS,
    PENDING_INFO,
    RESOLVED,
    VERIFIED,
    CLOSED;

    /** Statuses in which work is still outstanding (counts toward technician workload & SLA). */
    public static final Set<IncidentStatus> ACTIVE =
            EnumSet.of(NEW, CLASSIFIED, ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, PENDING_INFO);

    /** Statuses that occupy a technician. */
    public static final Set<IncidentStatus> TECHNICIAN_WORKLOAD =
            EnumSet.of(ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, PENDING_INFO);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public boolean isTerminal() {
        return this == CLOSED;
    }
}
