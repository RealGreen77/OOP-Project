package com.itsm.exception;

/** // [OOP: Inheritance] Specialisation of ResourceNotFoundException. */
public class IncidentNotFoundException extends ResourceNotFoundException {

    public IncidentNotFoundException(Object identifier) {
        super("INCIDENT_NOT_FOUND", "Incident", identifier);
    }
}
