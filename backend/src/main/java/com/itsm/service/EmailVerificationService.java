package com.itsm.service;

import com.itsm.config.ItsmProperties;
import com.itsm.domain.notification.DeliveryStatus;
import com.itsm.domain.user.EmailVerification;
import com.itsm.exception.BusinessRuleViolationException;
import com.itsm.exception.ExternalServiceException;
import com.itsm.exception.ResourceAlreadyExistsException;
import com.itsm.notification.EmailGateway;
import com.itsm.repository.EmailVerificationRepository;
import com.itsm.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

/**
 * Proves that a self-registering user controls the e-mail address they sign up with, by e-mailing a
 * one-time 6-digit code that must be presented when the account is created.
 *
 * Rules: one pending code per address; the code is stored only as a BCrypt hash; it expires after
 * {@code verification-code-minutes}; at most {@code verification-max-attempts} wrong guesses; a new
 * code can be requested only after {@code verification-resend-seconds}; a correct code is consumed.
 *
 * // [OOP: Abstraction] Callers see two operations - send a code, consume a code - not the storage,
 * //                    hashing or mail delivery behind them.
 * // [Pattern: Adapter] Delivery goes through the EmailGateway interface (SMTP, Mailpit or log).
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final EmailVerificationRepository verifications;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final EmailGateway emailGateway;
    private final ItsmProperties.Security rules;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(EmailVerificationRepository verifications, UserRepository users,
                                    PasswordEncoder passwordEncoder, EmailGateway emailGateway,
                                    ItsmProperties properties, Clock clock) {
        this.verifications = verifications;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.emailGateway = emailGateway;
        this.rules = properties.security();
        this.clock = clock;
    }

    public record CodeSent(String email, int expiresInMinutes, int resendAfterSeconds) {}

    /** Generates and e-mails a fresh code, replacing any pending one for the address. */
    @Transactional
    public CodeSent sendCode(String rawEmail) {
        String email = normalise(rawEmail);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResourceAlreadyExistsException("User", "email", email);
        }
        Instant now = clock.instant();
        EmailVerification pending = verifications.findByEmail(email).orElse(null);
        if (pending != null && pending.sentWithin(now, rules.verificationResendSeconds())) {
            long wait = pending.secondsUntilResend(now, rules.verificationResendSeconds());
            throw new BusinessRuleViolationException("CODE_RECENTLY_SENT",
                    "A code was just sent. Please wait " + wait + " seconds before requesting another.");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        deliver(email, code);

        String hash = passwordEncoder.encode(code);
        Instant expiry = now.plusSeconds(rules.verificationCodeMinutes() * 60L);
        if (pending == null) {
            verifications.save(new EmailVerification(email, hash, now, expiry));
        } else {
            pending.issue(hash, now, expiry);
        }
        return new CodeSent(email, rules.verificationCodeMinutes(), rules.verificationResendSeconds());
    }

    /**
     * Checks and consumes the code. A wrong guess is counted even though an exception is thrown,
     * hence noRollbackFor.
     */
    @Transactional(noRollbackFor = BusinessRuleViolationException.class)
    public void consumeCode(String rawEmail, String rawCode) {
        String email = normalise(rawEmail);
        String code = rawCode == null ? "" : rawCode.trim();
        if (code.isEmpty()) {
            throw new BusinessRuleViolationException("VERIFICATION_REQUIRED",
                    "Enter the 6-digit code we e-mailed to " + email);
        }
        EmailVerification pending = verifications.findByEmail(email).orElseThrow(() ->
                new BusinessRuleViolationException("CODE_INVALID", "No code was requested for this e-mail. Request a new code."));
        Instant now = clock.instant();
        if (pending.isExpired(now)) {
            throw new BusinessRuleViolationException("CODE_EXPIRED", "This code has expired. Request a new code.");
        }
        if (pending.attemptsExhausted(rules.verificationMaxAttempts())) {
            throw new BusinessRuleViolationException("CODE_ATTEMPTS_EXCEEDED",
                    "Too many wrong codes. Request a new code.");
        }
        if (!passwordEncoder.matches(code, pending.getCodeHash())) {
            pending.recordFailedAttempt();
            int left = Math.max(0, rules.verificationMaxAttempts() - pending.getAttempts());
            throw new BusinessRuleViolationException("CODE_INVALID",
                    left > 0 ? "Incorrect code. " + left + " attempt(s) left." : "Incorrect code. Request a new code.");
        }
        verifications.delete(pending); // single use
    }

    private void deliver(String email, String code) {
        String body = """
                Your I-ITSM verification code is: %s

                It expires in %d minutes. Enter it on the registration page to finish creating your account.

                If you did not try to register, you can ignore this e-mail.
                """.formatted(code, rules.verificationCodeMinutes());
        DeliveryStatus status;
        try {
            status = emailGateway.send(email, "[I-ITSM] Your verification code", body);
        } catch (ExternalServiceException ex) {
            log.warn("Verification e-mail to {} failed: {}", email, ex.getMessage());
            throw new ExternalServiceException("E-mail", "the verification code could not be sent. Please try again later", ex);
        }
        if (status == DeliveryStatus.SIMULATED) {
            // No real mailbox behind this address (log mode or a demo domain): surface the code to the developer.
            log.info("[SIMULATED VERIFICATION CODE] to={} code={}", email, code);
        }
    }

    private static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
