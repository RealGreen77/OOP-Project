package com.itsm.domain.user;

import com.itsm.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A pending one-time code proving that a registrant controls an e-mail address.
 *
 * // [OOP: Encapsulation] Only the hash of the code is kept; attempts and expiry change only through
 * //                      behaviour methods, so a code cannot be tried more often than the rules allow.
 */
@Entity
@Table(name = "email_verifications")
public class EmailVerification extends BaseEntity {

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_sent_at", nullable = false)
    private Instant lastSentAt;

    protected EmailVerification() {
    }

    public EmailVerification(String email, String codeHash, Instant now, Instant expiresAt) {
        this.email = email;
        issue(codeHash, now, expiresAt);
    }

    /** Replaces the code (a resend); the attempt counter starts again from zero. */
    public void issue(String newCodeHash, Instant now, Instant newExpiry) {
        this.codeHash = newCodeHash;
        this.lastSentAt = now;
        this.expiresAt = newExpiry;
        this.attempts = 0;
    }

    public void recordFailedAttempt() {
        attempts++;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean attemptsExhausted(int maxAttempts) {
        return attempts >= maxAttempts;
    }

    public boolean sentWithin(Instant now, long seconds) {
        return lastSentAt.plusSeconds(seconds).isAfter(now);
    }

    public long secondsUntilResend(Instant now, long cooldownSeconds) {
        return Math.max(1, lastSentAt.plusSeconds(cooldownSeconds).getEpochSecond() - now.getEpochSecond());
    }

    public String getEmail() { return email; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }
}
