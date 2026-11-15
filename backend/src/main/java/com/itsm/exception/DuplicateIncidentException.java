package com.itsm.exception;

import java.util.List;
import java.util.Map;

/**
 * The same reporter is submitting what appears to be the same incident again within a short window.
 * Clients may resubmit with {@code force=true} after reviewing the existing incident(s).
 */
public class DuplicateIncidentException extends ItsmException {

    private final List<String> existingIncidentNumbers;

    public DuplicateIncidentException(List<String> existingIncidentNumbers) {
        super("DUPLICATE_INCIDENT", "You recently reported a very similar incident: "
                + String.join(", ", existingIncidentNumbers)
                + ". Add information to it instead, or resubmit with force=true.");
        this.existingIncidentNumbers = List.copyOf(existingIncidentNumbers);
    }

    @Override
    public ErrorType type() {
        return ErrorType.CONFLICT;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of("existingIncidents", existingIncidentNumbers);
    }
}
