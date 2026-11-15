package com.itsm.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Per-user opt-ins for notification channels. In-app notifications are always on.
 * // [OOP: Composition] Value object embedded in (and owned by) a User.
 */
@Embeddable
public class NotificationPreferences {

    @Column(name = "pref_email", nullable = false)
    private boolean emailEnabled = true;

    @Column(name = "pref_sms", nullable = false)
    private boolean smsEnabled = false;

    protected NotificationPreferences() {
    }

    public NotificationPreferences(boolean emailEnabled, boolean smsEnabled) {
        this.emailEnabled = emailEnabled;
        this.smsEnabled = smsEnabled;
    }

    public static NotificationPreferences defaults() {
        return new NotificationPreferences(true, false);
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public boolean isSmsEnabled() {
        return smsEnabled;
    }
}
