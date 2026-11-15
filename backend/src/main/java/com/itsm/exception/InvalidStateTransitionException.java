package com.itsm.exception;

import com.itsm.domain.incident.IncidentAction;
import com.itsm.domain.incident.IncidentStatus;

import java.util.Map;

/** An action is not permitted in the incident's current lifecycle state (e.g. NEW -> CLOSED). */
public class InvalidStateTransitionException extends ItsmException {

    private final IncidentStatus currentStatus;
    private final IncidentAction attemptedAction;

    public InvalidStateTransitionException(IncidentStatus currentStatus, IncidentAction attemptedAction) {
        super("INVALID_STATE_TRANSITION",
                "Cannot perform " + attemptedAction + " on an incident in status " + currentStatus);
        this.currentStatus = currentStatus;
        this.attemptedAction = attemptedAction;
    }

    public IncidentStatus getCurrentStatus() {
        return currentStatus;
    }

    public IncidentAction getAttemptedAction() {
        return attemptedAction;
    }

    @Override
    public ErrorType type() {
        return ErrorType.CONFLICT;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of("currentStatus", currentStatus.name(), "attemptedAction", attemptedAction.name());
    }
}
