package com.itsm.service;

import com.itsm.config.ItsmProperties;
import com.itsm.domain.audit.AuditAction;
import com.itsm.domain.user.NotificationPreferences;
import com.itsm.domain.user.Role;
import com.itsm.domain.user.User;
import com.itsm.domain.user.UserFactory;
import com.itsm.exception.AccountLockedException;
import com.itsm.exception.AuthenticationFailedException;
import com.itsm.exception.BusinessRuleViolationException;
import com.itsm.exception.ResourceAlreadyExistsException;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.UserRepository;
import com.itsm.security.CurrentUserProvider;
import com.itsm.security.JwtService;
import com.itsm.security.UserPrincipal;
import com.itsm.web.dto.DtoMapper;
import com.itsm.web.dto.UserDtos.AuthResponse;
import com.itsm.web.dto.UserDtos.ChangePasswordRequest;
import com.itsm.web.dto.UserDtos.LoginRequest;
import com.itsm.web.dto.UserDtos.RegisterRequest;
import com.itsm.web.dto.UserDtos.UpdateProfileRequest;
import com.itsm.web.dto.UserDtos.UserDetail;
import com.itsm.domain.incident.IncidentStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Registration, login (with brute-force lockout) and self-service profile management.
 * // [Security] BCrypt hashing, generic error messages (no user enumeration), lockout after N failures.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final IncidentRepository incidentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final UserCodeGenerator codeGenerator;
    private final CurrentUserProvider currentUser;
    private final DtoMapper mapper;
    private final ItsmProperties properties;
    private final Clock clock;
    private final EmailVerificationService emailVerification;

    public AuthService(UserRepository userRepository, IncidentRepository incidentRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService, AuditService auditService,
                       UserCodeGenerator codeGenerator, CurrentUserProvider currentUser, DtoMapper mapper,
                       ItsmProperties properties, Clock clock, EmailVerificationService emailVerification) {
        this.userRepository = userRepository;
        this.incidentRepository = incidentRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.codeGenerator = codeGenerator;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
        this.emailVerification = emailVerification;
        this.dummyHash = passwordEncoder.encode("timing-equaliser-not-a-real-password");
    }

    private final String dummyHash;

    /**
     * Self-registration always creates an Employee; support staff and admins are created by administrators.
     * The registrant must present the one-time code e-mailed to the address (see EmailVerificationService).
     * noRollbackFor: a wrong code must still count as a failed attempt.
     */
    @Transactional(noRollbackFor = BusinessRuleViolationException.class)
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ResourceAlreadyExistsException("User", "email", request.email());
        }
        if (properties.security().emailVerificationRequired()) {
            emailVerification.consumeCode(request.email(), request.verificationCode());
        }
        User user = UserFactory.create(Role.EMPLOYEE, codeGenerator.next(Role.EMPLOYEE), request.fullName(),
                request.email(), passwordEncoder.encode(request.password()), request.department(), null, null);
        user.updateProfile(request.fullName(), request.department(), blankToNull(request.phone()));
        user.recordSuccessfulLogin(clock.instant());
        userRepository.save(user);
        auditService.recordAction(user, AuditAction.USER_CREATED, AuditService.USER, user.getId(), user.getUserCode(),
                null, Role.EMPLOYEE.name(), "Self-registered as employee " + user.displayName()
                        + (properties.security().emailVerificationRequired() ? " (e-mail verified)" : ""));
        return tokenFor(user);
    }

    /**
     * noRollbackFor: the failed-attempt counter and lock must persist even though we throw.
     */
    @Transactional(noRollbackFor = {AuthenticationFailedException.class, AccountLockedException.class})
    public AuthResponse login(LoginRequest request) {
        Instant now = clock.instant();
        User user = userRepository.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        if (user == null) {
            // Still run a hash comparison so response time does not reveal whether the e-mail exists.
            passwordEncoder.matches(request.password(), dummyHash);
            auditService.recordSecurityEvent(AuditAction.LOGIN_FAILED, null, request.email(), "Login failed: unknown e-mail");
            throw new AuthenticationFailedException();
        }
        if (!user.isActive()) {
            throw new AccountLockedException(null);
        }
        if (user.isLocked(now)) {
            throw new AccountLockedException(user.getLockedUntil());
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean locked = user.recordFailedLogin(now, properties.security().maxFailedLogins(),
                    Duration.ofMinutes(properties.security().lockMinutes()));
            auditService.recordSecurityEvent(locked ? AuditAction.ACCOUNT_LOCKED : AuditAction.LOGIN_FAILED,
                    user.getId(), user.displayName(), locked ? "Account locked after repeated failed logins" : "Login failed: wrong password");
            if (locked) throw new AccountLockedException(user.getLockedUntil());
            throw new AuthenticationFailedException();
        }
        user.recordSuccessfulLogin(now);
        auditService.recordSecurityEvent(AuditAction.LOGIN_SUCCESS, user.getId(), user.displayName(), "Logged in");
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public UserDetail me() {
        User user = currentUser.currentUser();
        return mapper.userDetail(user, activeIncidents(user));
    }

    @Transactional
    public UserDetail updateProfile(UpdateProfileRequest request) {
        User user = currentUser.currentUser();
        user.updateProfile(request.fullName(), request.department(), blankToNull(request.phone()));
        user.updatePreferences(new NotificationPreferences(request.emailNotifications(), request.smsNotifications()));
        return mapper.userDetail(user, activeIncidents(user));
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = currentUser.currentUser();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleViolationException("WRONG_PASSWORD", "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleViolationException("PASSWORD_REUSED", "New password must differ from the current one");
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        auditService.recordAction(user, AuditAction.USER_UPDATED, AuditService.USER, user.getId(), user.getUserCode(),
                null, null, "Password changed");
    }

    private AuthResponse tokenFor(User user) {
        JwtService.IssuedToken token = jwtService.issue(UserPrincipal.from(user));
        return new AuthResponse(token.token(), token.expiresAt(), mapper.userDetail(user, activeIncidents(user)));
    }

    private int activeIncidents(User user) {
        if (!user.getRole().isSupportStaff() || user.getId() == null) return 0;
        return (int) incidentRepository.countByAssignedToIdAndStatusIn(user.getId(), IncidentStatus.TECHNICIAN_WORKLOAD);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
