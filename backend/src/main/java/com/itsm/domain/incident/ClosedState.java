package com.itsm.domain.incident;

import java.util.EnumSet;
import java.util.Set;

/** CLOSED: terminal state. Only late feedback is accepted. */
final class ClosedState extends IncidentState {

    private static final Set<IncidentAction> ALLOWED = EnumSet.of(IncidentAction.FEEDBACK);

    @Override
    public IncidentStatus status() {
        return IncidentStatus.CLOSED;
    }

    @Override
    public Set<IncidentAction> allowedActions() {
        return ALLOWED;
    }
}
