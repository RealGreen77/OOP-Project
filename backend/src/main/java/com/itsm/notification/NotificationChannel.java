package com.itsm.notification;

import com.itsm.domain.notification.DeliveryStatus;
import com.itsm.domain.notification.NotificationChannelType;
import com.itsm.domain.user.User;
import com.itsm.exception.ExternalServiceException;

/**
 * A mechanism for delivering notifications.
 *
 * // [OOP: Interfaces + Polymorphism] The dispatcher loops over channels through this contract only.
 * // [Extensibility] A new mechanism (push, Slack, Teams) = one new class implementing this interface
 * //                 + one enum constant. Nothing else in the system changes.
 * // [MVP: Notification] At least two mechanisms: IN_APP (real-time WebSocket), EMAIL (SMTP), SMS (simulated).
 */
public interface NotificationChannel {

    NotificationChannelType type();

    /** Whether this channel should be used for this recipient and message (preferences, urgency...). */
    boolean supports(User recipient, NotificationMessage message);

    /** Address used by this channel (email, phone) or null for in-app. */
    String destinationFor(User recipient);

    /**
     * Delivers the message.
     * @throws ExternalServiceException when the underlying provider fails (network failure, etc.)
     */
    DeliveryStatus deliver(User recipient, NotificationMessage message);
}
