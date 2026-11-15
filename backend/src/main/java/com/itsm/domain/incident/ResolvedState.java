package com.itsm.domain.incident;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** RESOLVED: a fix was provided. The reporter verifies it or reopens the incident. */
final class ResolvedState extends IncidentState {

    private static final Set<IncidentAction> ALLOWED = EnumSet.of(
            IncidentAction.VERIFY, IncidentAction.REOPEN, IncidentAction.ADD_NOTE, IncidentAction.FEEDBACK);

    @Override
    public IncidentStatus status() {
        return IncidentStatus.RESOLVED;
    }

    @Override
    public Set<IncidentAction> allowedActions() {
        return ALLOWED;
    }

    @Override
    void verify(Incident incident, Instant at) {
        incident.markVerified(at);
        incident.changeStatus(IncidentStatus.VERIFIED);
    }

    /** Fix did not work: back to the same technician, still IN_PROGRESS. */
    @Override
    void reopen(Incident incident, Instant at) {
        incident.markReopened();
        incident.changeStatus(IncidentStatus.IN_PROGRESS);
    }
}
