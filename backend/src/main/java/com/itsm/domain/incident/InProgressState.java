package com.itsm.domain.incident;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** IN_PROGRESS: under investigation. May wait on the reporter or be resolved. */
final class InProgressState extends ActiveWorkState {

    private static final Set<IncidentAction> ALLOWED = EnumSet.of(
            IncidentAction.REQUEST_INFO, IncidentAction.RESOLVE, IncidentAction.REASSIGN,
            IncidentAction.CLASSIFY, IncidentAction.MARK_DUPLICATE,
            IncidentAction.UPDATE_DETAILS, IncidentAction.ADD_NOTE);

    @Override
    public IncidentStatus status() {
        return IncidentStatus.IN_PROGRESS;
    }

    @Override
    public Set<IncidentAction> allowedActions() {
        return ALLOWED;
    }

    /** Waiting on the reporter pauses the SLA clock. */
    @Override
    void requestInfo(Incident incident, Instant at) {
        incident.pauseSla(at);
        incident.changeStatus(IncidentStatus.PENDING_INFO);
    }

    @Override
    void resolve(Incident incident, Resolution resolution, Instant at) {
        incident.applyResolution(resolution, at);
        incident.changeStatus(IncidentStatus.RESOLVED);
    }
}
