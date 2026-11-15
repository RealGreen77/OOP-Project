package com.itsm.domain.notification;

/** Supported delivery mechanisms. Adding one = new enum constant + new NotificationChannel implementation. */
public enum NotificationChannelType {
    IN_APP,
    EMAIL,
    SMS
}
