package com.itsm.exception;

/** The referenced user does not exist. */
public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(Object identifier) {
        super("USER_NOT_FOUND", "User", identifier);
    }
}
