package com.itsm.exception;

/** A unique attribute (email, service code, ...) is already in use. */
public class ResourceAlreadyExistsException extends ItsmException {

    public ResourceAlreadyExistsException(String resource, String attribute, Object value) {
        super(resource.toUpperCase().replace(' ', '_') + "_ALREADY_EXISTS",
                resource + " with " + attribute + " '" + value + "' already exists");
    }

    @Override
    public ErrorType type() {
        return ErrorType.CONFLICT;
    }
}
