package com.itsm.exception;

/** No technician (or not the requested technician) is available to take the incident. */
public class TechnicianUnavailableException extends ItsmException {

    public TechnicianUnavailableException(String message) {
        super("TECHNICIAN_UNAVAILABLE", message);
    }

    @Override
    public ErrorType type() {
        return ErrorType.CONFLICT;
    }
}
