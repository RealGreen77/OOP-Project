package com.itsm.domain.incident;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** PENDING_INFO: waiting for the reporter to supply information. The SLA clock is paused. */
final class PendingInfoState extends IncidentState {

    private static final Set<IncidentAction> ALLOWED = EnumSet.of(
            IncidentAction.PROVIDE_INFO, IncidentAction.UPDATE_DETAILS, IncidentAction.ADD_NOTE);

    @Override
    public IncidentStatus status() {
        return IncidentStatus.PENDING_INFO;
    }

    @Override
    public Set<IncidentAction> allowedActions() {
        return ALLOWED;
    }

    @Override
    void provideInfo(Incident incident, Instant at) {
        incident.resumeSla(at);
        incident.changeStatus(IncidentStatus.IN_PROGRESS);
    }
}
