package com.itsm.exception;

/** The authenticated user is not allowed to perform the requested operation. */
public class UnauthorizedOperationException extends ItsmException {

    public UnauthorizedOperationException(String message) {
        super("UNAUTHORIZED_OPERATION", message);
    }

    @Override
    public ErrorType type() {
        return ErrorType.FORBIDDEN;
    }
}
