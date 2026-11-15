package com.itsm.exception;

import java.util.Map;

/** A requested entity does not exist. Specialised by resource-specific subclasses. */
public class ResourceNotFoundException extends ItsmException {

    private final String resource;
    private final Object identifier;

    public ResourceNotFoundException(String resource, Object identifier) {
        this(resource.toUpperCase().replace(' ', '_') + "_NOT_FOUND", resource, identifier);
    }

    protected ResourceNotFoundException(String errorCode, String resource, Object identifier) {
        super(errorCode, resource + " not found: " + identifier);
        this.resource = resource;
        this.identifier = identifier;
    }

    @Override
    public ErrorType type() {
        return ErrorType.NOT_FOUND;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of("resource", resource, "identifier", String.valueOf(identifier));
    }
}
