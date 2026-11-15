package com.itsm.notification;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * Pushes real-time updates over STOMP/WebSocket.
 *  - {@code /user/queue/notifications}: private, per-user notification stream (authenticated by JWT)
 *  - {@code /topic/incidents}: incident change feed that keeps dashboards and lists live
 * Messages are sent only after the surrounding transaction commits, so clients never see rolled-back data.
 *
 * // [Innovation] Real-time notifications and real-time operational dashboard.
 */
@Component
public class RealtimePublisher {

    public static final String USER_QUEUE = "/queue/notifications";
    public static final String INCIDENT_TOPIC = "/topic/incidents";

    private final SimpMessagingTemplate messaging;

    public RealtimePublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void toUser(Long userId, Object payload) {
        afterCommit(() -> messaging.convertAndSendToUser(String.valueOf(userId), USER_QUEUE, payload));
    }

    public void incidentChanged(Long incidentId, String number, String eventType, String status) {
        Map<String, Object> payload = Map.of(
                "incidentId", incidentId,
                "number", number == null ? "" : number,
                "eventType", eventType,
                "status", status);
        afterCommit(() -> messaging.convertAndSend(INCIDENT_TOPIC, payload));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safely(action);
                }
            });
        } else {
            safely(action);
        }
    }

    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // Real-time push is best effort; clients also poll/refresh.
        }
    }
}
