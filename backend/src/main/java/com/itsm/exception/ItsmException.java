package com.itsm.exception;

import java.util.Collections;
import java.util.Map;

/**
 * Root of the application's exception hierarchy.
 *
 * // [OOP: Abstraction + Inheritance] Every business failure "is-a" ItsmException.
 * // [OOP: Polymorphism] Subclasses override {@link #type()}; the global handler treats them uniformly.
 * // [OOP: Exception Handling] Unchecked so business code stays readable; translated centrally.
 */
public abstract class ItsmException extends RuntimeException {

    private final String errorCode;

    protected ItsmException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected ItsmException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** Category used to choose the HTTP status. */
    public abstract ErrorType type();

    /** Stable machine-readable code (e.g. {@code INVALID_STATE_TRANSITION}) for clients. */
    public String getErrorCode() {
        return errorCode;
    }

    /** Optional structured details included in the error response. */
    public Map<String, Object> details() {
        return Collections.emptyMap();
    }
}
