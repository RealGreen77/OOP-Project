package com.itsm.domain.incident;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** VERIFIED: the reporter confirmed the fix. Ready to be closed. */
final class VerifiedState extends IncidentState {

    private static final Set<IncidentAction> ALLOWED = EnumSet.of(
            IncidentAction.CLOSE, IncidentAction.ADD_NOTE, IncidentAction.FEEDBACK);

    @Override
    public IncidentStatus status() {
        return IncidentStatus.VERIFIED;
    }

    @Override
    public Set<IncidentAction> allowedActions() {
        return ALLOWED;
    }

    @Override
    void close(Incident incident, ClosureCode code, String notes, Instant at) {
        incident.applyClosure(code, notes, at);
        incident.changeStatus(IncidentStatus.CLOSED);
    }
}
