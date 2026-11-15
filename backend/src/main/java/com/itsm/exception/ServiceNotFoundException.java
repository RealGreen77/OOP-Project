package com.itsm.exception;

/** The referenced IT service does not exist or is inactive. */
public class ServiceNotFoundException extends ResourceNotFoundException {

    public ServiceNotFoundException(Object identifier) {
        super("SERVICE_NOT_FOUND", "IT service", identifier);
    }
}
