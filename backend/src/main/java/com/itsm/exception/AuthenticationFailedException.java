package com.itsm.exception;

/** Invalid login credentials. Message is deliberately generic to avoid user enumeration. */
public class AuthenticationFailedException extends ItsmException {

    public AuthenticationFailedException() {
        super("INVALID_CREDENTIALS", "Invalid email or password");
    }

    @Override
    public ErrorType type() {
        return ErrorType.AUTHENTICATION;
    }
}
