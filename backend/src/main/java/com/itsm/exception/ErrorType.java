package com.itsm.exception;

/**
 * Transport-neutral error categories. The web layer maps these to HTTP status codes,
 * keeping the domain free of any web-framework dependency.
 */
public enum ErrorType {
    VALIDATION(400),
    AUTHENTICATION(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    CONFLICT(409),
    BUSINESS_RULE(422),
    LOCKED(423),
    EXTERNAL_SERVICE(502),
    UNAVAILABLE(503);

    private final int httpStatus;

    ErrorType(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
