package com.itsm.domain.user;

import com.itsm.domain.common.BaseEntity;
import com.itsm.exception.InvalidInputException;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;

/**
 * Abstract base for every person who uses the system.
 *
 * // [OOP: Abstraction] A "User" is never instantiated directly - only concrete roles are.
 * // [OOP: Inheritance] Employee, Technician (-> TeamLead), Administrator (-> ITManager) each "is-a" User.
 * // [OOP: Polymorphism] {@link #getRole()} and {@link #permissions()} are overridden by each subclass;
 * //                    security checks call {@link #can(Permission)} without knowing the concrete type.
 * // [OOP: Encapsulation] The password hash is write-only via a guarded method and never exposed in DTOs;
 * //                    login-lockout state can only change through intention-revealing methods.
 */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "user_type", length = 20)
public abstract class User extends BaseEntity {

    @Column(name = "user_code", nullable = false, unique = true, length = 20)
    private String userCode;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 80)
    private String department;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Embedded
    private NotificationPreferences preferences = NotificationPreferences.defaults();

    /** Required by JPA. */
    protected User() {
    }

    protected User(String userCode, String fullName, String email, String passwordHash, String department) {
        this.userCode = requireText(userCode, "userCode");
        this.fullName = requireText(fullName, "fullName");
        this.email = requireText(email, "email").trim().toLowerCase();
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.department = department;
    }

    // ---------------------------------------------------------------- polymorphic behaviour

    /** The role this concrete user type represents. */
    public abstract Role getRole();

    /** Capabilities granted to this type of user. */
    public abstract Set<Permission> permissions();

    /** Template-style check used throughout the service layer. */
    public final boolean can(Permission permission) {
        return active && permissions().contains(permission);
    }

    // ---------------------------------------------------------------- login security

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Records a failed login and locks the account once {@code maxAttempts} is reached.
     * @return true if this attempt caused the account to become locked
     */
    public boolean recordFailedLogin(Instant now, int maxAttempts, Duration lockDuration) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedLoginAttempts = 0;
            return true;
        }
        return false;
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void changePasswordHash(String newHash) {
        this.passwordHash = requireText(newHash, "passwordHash");
    }

    // ---------------------------------------------------------------- profile management

    public void updateProfile(String fullName, String department, String phone) {
        this.fullName = requireText(fullName, "fullName");
        this.department = department;
        this.phone = phone;
    }

    public void updatePreferences(NotificationPreferences preferences) {
        this.preferences = preferences == null ? NotificationPreferences.defaults() : preferences;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    // ---------------------------------------------------------------- accessors

    public String getUserCode() { return userCode; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDepartment() { return department; }
    public String getPhone() { return phone; }
    public boolean isActive() { return active; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }

    public NotificationPreferences getPreferences() {
        return preferences == null ? NotificationPreferences.defaults() : preferences;
    }

    /** "E102 Asha Rao" style label used in audit trails. */
    public String displayName() {
        return userCode + " " + fullName;
    }

    protected static Set<Permission> immutable(Set<Permission> permissions) {
        return Collections.unmodifiableSet(permissions);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException(field + " must not be blank");
        }
        return value;
    }
}
