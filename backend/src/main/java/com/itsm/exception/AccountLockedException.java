package com.itsm.exception;

import java.time.Instant;
import java.util.Map;

/** Raised after too many failed login attempts (brute-force protection) or for deactivated accounts. */
public class AccountLockedException extends ItsmException {

    private final Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil) {
        super("ACCOUNT_LOCKED", lockedUntil == null
                ? "This account has been deactivated. Contact an administrator."
                : "Account temporarily locked due to repeated failed logins. Try again later.");
        this.lockedUntil = lockedUntil;
    }

    @Override
    public ErrorType type() {
        return ErrorType.LOCKED;
    }

    @Override
    public Map<String, Object> details() {
        return lockedUntil == null ? Map.of() : Map.of("lockedUntil", lockedUntil.toString());
    }
}
