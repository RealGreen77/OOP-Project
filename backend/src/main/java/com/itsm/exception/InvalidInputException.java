package com.itsm.exception;

/** Input that passed syntactic validation but is semantically invalid. */
public class InvalidInputException extends ItsmException {

    public InvalidInputException(String message) {
        super("INVALID_INPUT", message);
    }

    @Override
    public ErrorType type() {
        return ErrorType.VALIDATION;
    }
}
