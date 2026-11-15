package com.itsm.domain.incident;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** ASSIGNED: a technician owns the incident but has not yet acknowledged it (SLA response clock running). */
final class AssignedState extends ActiveWorkState {

    private static final Set<IncidentAction> ALLOWED = EnumSet.of(
            IncidentAction.ACKNOWLEDGE, IncidentAction.REASSIGN, IncidentAction.CLASSIFY,
            IncidentAction.MARK_DUPLICATE, IncidentAction.UPDATE_DETAILS, IncidentAction.ADD_NOTE);

    @Override
    public IncidentStatus status() {
        return IncidentStatus.ASSIGNED;
    }

    @Override
    public Set<IncidentAction> allowedActions() {
        return ALLOWED;
    }

    @Override
    void acknowledge(Incident incident, Instant at) {
        incident.markAcknowledged(at);
        incident.changeStatus(IncidentStatus.ACKNOWLEDGED);
    }
}
