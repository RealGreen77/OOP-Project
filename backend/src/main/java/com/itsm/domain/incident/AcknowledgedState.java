package com.itsm.domain.incident;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** ACKNOWLEDGED: the technician accepted the incident; investigation can begin. */
final class AcknowledgedState extends ActiveWorkState {

    private static final Set<IncidentAction> ALLOWED = EnumSet.of(
            IncidentAction.START_WORK, IncidentAction.REASSIGN, IncidentAction.CLASSIFY,
            IncidentAction.MARK_DUPLICATE, IncidentAction.UPDATE_DETAILS, IncidentAction.ADD_NOTE);

    @Override
    public IncidentStatus status() {
        return IncidentStatus.ACKNOWLEDGED;
    }

    @Override
    public Set<IncidentAction> allowedActions() {
        return ALLOWED;
    }

    @Override
    void startWork(Incident incident, Instant at) {
        incident.changeStatus(IncidentStatus.IN_PROGRESS);
    }
}
