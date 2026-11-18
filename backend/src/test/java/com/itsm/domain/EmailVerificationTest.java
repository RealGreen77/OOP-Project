package com.itsm.domain;

import com.itsm.domain.user.EmailVerification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.itsm.TestData.T0;
import static org.assertj.core.api.Assertions.assertThat;

/** Rules of a one-time registration code: expiry boundary, attempt budget and resend cooldown. */
class EmailVerificationTest {

    private final EmailVerification v = new EmailVerification("a@b.org", "hash", T0, T0.plus(Duration.ofMinutes(10)));

    @Test
    @DisplayName("Boundary: a code is valid until just before its expiry instant, expired at and after it")
    void expiryBoundary() {
        assertThat(v.isExpired(T0.plus(Duration.ofMinutes(10)).minusSeconds(1))).isFalse();
        assertThat(v.isExpired(T0.plus(Duration.ofMinutes(10)))).isTrue();
    }

    @Test
    @DisplayName("Attempts are counted up to the limit and reset when a new code is issued")
    void attempts() {
        for (int i = 0; i < 4; i++) v.recordFailedAttempt();
        assertThat(v.attemptsExhausted(5)).isFalse();
        v.recordFailedAttempt();
        assertThat(v.attemptsExhausted(5)).isTrue();
        v.issue("new-hash", T0.plusSeconds(60), T0.plusSeconds(660));
        assertThat(v.getAttempts()).isZero();
        assertThat(v.getCodeHash()).isEqualTo("new-hash");
    }

    @Test
    @DisplayName("Resend cooldown: blocked for 30 s after sending, with the remaining wait reported")
    void resendCooldown() {
        assertThat(v.sentWithin(T0.plusSeconds(10), 30)).isTrue();
        assertThat(v.secondsUntilResend(T0.plusSeconds(10), 30)).isEqualTo(20);
        assertThat(v.sentWithin(T0.plusSeconds(30), 30)).isFalse();
    }
}
