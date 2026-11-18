package com.itsm.service;

import com.itsm.config.ItsmProperties;
import com.itsm.domain.audit.AuditAction;
import com.itsm.domain.notification.DeliveryStatus;
import com.itsm.domain.user.PasswordResetToken;
import com.itsm.domain.user.User;
import com.itsm.exception.BusinessRuleViolationException;
import com.itsm.exception.ExternalServiceException;
import com.itsm.notification.EmailGateway;
import com.itsm.repository.PasswordResetTokenRepository;
import com.itsm.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Self-service "forgot password": e-mails a single-use link that lets the owner of the address set a
 * new password.
 *
 * Security rules:
 * - The response never reveals whether an address has an account (no user enumeration).
 * - The token is 256 bits of randomness; only its SHA-256 hash is stored, so a database leak cannot be
 *   turned into working links.
 * - Links expire after {@code password-reset-minutes}, work once, and issuing a new link revokes older ones.
 * - At most one link per user per minute, so the feature cannot be used to flood someone's inbox.
 * - A successful reset clears any login lockout and is recorded in the audit trail.
 *
 * // [Pattern: Adapter] Delivery goes through the EmailGateway interface.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final long RESEND_COOLDOWN_SECONDS = 60;

    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final EmailGateway emailGateway;
    private final AuditService auditService;
    private final ItsmProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(PasswordResetTokenRepository tokens, UserRepository users, PasswordEncoder passwordEncoder,
                                EmailGateway emailGateway, AuditService auditService, ItsmProperties properties, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.emailGateway = emailGateway;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    /** Always completes silently, whether or not the address belongs to an active account. */
    @Transactional
    public void requestReset(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim();
        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !user.isActive()) {
            log.debug("Password reset requested for unknown or inactive address");
            return;
        }
        Instant now = clock.instant();
        if (tokens.existsByUserIdAndCreatedAtAfter(user.getId(), now.minusSeconds(RESEND_COOLDOWN_SECONDS))) {
            return; // a link was sent less than a minute ago
        }
        tokens.findByUserIdAndUsedAtIsNull(user.getId()).forEach(t -> t.markUsed(now)); // revoke older links

        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        int minutes = properties.security().passwordResetMinutes();
        tokens.save(new PasswordResetToken(user, sha256(token), now.plusSeconds(minutes * 60L)));

        String link = properties.notifications().frontendBaseUrl() + "/reset-password?token=" + token;
        String body = """
                Hello %s,

                We received a request to reset your I-ITSM password. Open this link to choose a new one:

                %s

                The link works once and expires in %d minutes.
                If you did not ask for this, ignore this e-mail - your password stays the same.
                """.formatted(user.getFullName(), link, minutes);
        try {
            DeliveryStatus status = emailGateway.send(user.getEmail(), "[I-ITSM] Reset your password", body);
            if (status == DeliveryStatus.SIMULATED) {
                log.info("[SIMULATED PASSWORD RESET LINK] to={} link={}", user.getEmail(), link);
            }
        } catch (ExternalServiceException ex) {
            // Do not reveal delivery problems to the caller (that would confirm the account exists).
            log.warn("Password reset e-mail to {} failed: {}", user.getEmail(), ex.getMessage());
        }
        auditService.recordSecurityEvent(AuditAction.USER_UPDATED, user.getId(), user.displayName(),
                "Password reset link requested");
    }

    /** Sets a new password if the token is valid; the token is then consumed. */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken reset = token == null ? null : tokens.findByTokenHash(sha256(token.trim())).orElse(null);
        if (reset == null || !reset.isUsable(now) || !reset.getUser().isActive()) {
            throw new BusinessRuleViolationException("RESET_LINK_INVALID",
                    "This reset link is invalid, already used or expired. Request a new one.");
        }
        User user = reset.getUser();
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessRuleViolationException("PASSWORD_REUSED", "New password must differ from the current one");
        }
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        user.clearLockout();
        reset.markUsed(now);
        auditService.recordAction(user, AuditAction.USER_UPDATED, AuditService.USER, user.getId(), user.getUserCode(),
                null, null, "Password reset via e-mailed link");
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
