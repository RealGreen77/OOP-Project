package com.itsm.exception;

/** A domain rule was violated (e.g. feedback on an unresolved incident). */
public class BusinessRuleViolationException extends ItsmException {

    public BusinessRuleViolationException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public ErrorType type() {
        return ErrorType.BUSINESS_RULE;
    }
}
