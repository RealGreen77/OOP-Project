package com.itsm.domain.notification;

import com.itsm.domain.common.BaseEntity;
import com.itsm.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A notification delivered (or attempted) to one user through one channel.
 * IN_APP rows form the user's inbox; EMAIL/SMS rows form the delivery log.
 */
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationChannelType channel;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "incident_id")
    private Long incidentId;

    @Column(name = "incident_number", length = 20)
    private String incidentNumber;

    @Column(length = 160)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 12)
    private DeliveryStatus deliveryStatus;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public Notification(User recipient, NotificationChannelType channel, String eventType, String title,
                        String message, Long incidentId, String incidentNumber, String destination, Instant at) {
        this.recipient = recipient;
        this.channel = channel;
        this.eventType = eventType;
        this.title = truncate(title, 200);
        this.message = truncate(message, 2000);
        this.incidentId = incidentId;
        this.incidentNumber = incidentNumber;
        this.destination = destination;
        this.deliveryStatus = DeliveryStatus.DELIVERED;
        initialiseCreatedAt(at);
    }

    public void markDelivered(DeliveryStatus status) {
        this.deliveryStatus = status;
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.deliveryStatus = DeliveryStatus.FAILED;
        this.failureReason = truncate(reason, 500);
    }

    public void markRead(Instant at) {
        if (!read) {
            read = true;
            readAt = at;
        }
    }

    private static String truncate(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) : s);
    }

    public User getRecipient() { return recipient; }
    public NotificationChannelType getChannel() { return channel; }
    public String getEventType() { return eventType; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public Long getIncidentId() { return incidentId; }
    public String getIncidentNumber() { return incidentNumber; }
    public String getDestination() { return destination; }
    public DeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public String getFailureReason() { return failureReason; }
    public boolean isRead() { return read; }
    public Instant getReadAt() { return readAt; }
}
