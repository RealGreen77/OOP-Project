package com.itsm.service;

import com.itsm.config.ItsmProperties;
import com.itsm.domain.incident.Incident;
import com.itsm.domain.incident.IncidentStatus;
import com.itsm.engine.escalation.EscalationChain;
import com.itsm.events.IncidentEvent;
import com.itsm.events.IncidentEventPublisher;
import com.itsm.repository.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Continuous SLA monitoring: detects approaching deadlines, response/resolution violations and triggers
 * automatic escalation through the Chain of Responsibility.
 *
 * Escalation rules:
 *  1. Urgent (P1/P2) incidents not acknowledged within N x response target escalate to level N (N = 1..3).
 *  2. Any resolution breach escalates one level (if not already at the top).
 *
 * // [MVP: SLA Management] Deadline tracking, violation detection, warnings before deadlines, record violations.
 * // [Intelligent Feature C] Automatic Escalation.
 */
@Service
public class SlaMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitoringService.class);

    private final IncidentRepository incidentRepository;
    private final EscalationChain escalationChain;
    private final IncidentEventPublisher publisher;
    private final ItsmProperties properties;
    private final Clock clock;

    public SlaMonitoringService(IncidentRepository incidentRepository, EscalationChain escalationChain,
                                IncidentEventPublisher publisher, ItsmProperties properties, Clock clock) {
        this.incidentRepository = incidentRepository;
        this.escalationChain = escalationChain;
        this.publisher = publisher;
        this.properties = properties;
        this.clock = clock;
    }

    public record ScanResult(int scanned, int warnings, int breaches, int escalations) {}

    @Transactional
    public ScanResult scan() {
        Instant now = clock.instant();
        List<Incident> active = incidentRepository.findByStatusIn(IncidentStatus.ACTIVE);
        int warnings = 0, breaches = 0, escalations = 0;
        for (Incident incident : active) {
            if (!incident.hasSla()) continue;
            if (incident.checkResponseBreach(now)) {
                publisher.publish(new IncidentEvent.SlaBreached(incident, now, IncidentEvent.SlaBreached.Kind.RESPONSE));
                breaches++;
            }
            boolean resolutionBreached = incident.checkResolutionBreach(now);
            if (resolutionBreached) {
                publisher.publish(new IncidentEvent.SlaBreached(incident, now, IncidentEvent.SlaBreached.Kind.RESOLUTION));
                breaches++;
            } else if (incident.checkSlaWarning(now)) {
                publisher.publish(new IncidentEvent.SlaWarning(incident, now, incident.getSla().elapsedFraction(now)));
                warnings++;
            }
            if (properties.sla().autoEscalation() && shouldEscalate(incident, now, resolutionBreached)) {
                if (escalate(incident, now, resolutionBreached)) escalations++;
            }
        }
        if (warnings + breaches + escalations > 0) {
            log.info("SLA scan: {} active, {} warnings, {} breaches, {} escalations", active.size(), warnings, breaches, escalations);
        }
        return new ScanResult(active.size(), warnings, breaches, escalations);
    }

    private boolean shouldEscalate(Incident incident, Instant now, boolean resolutionJustBreached) {
        if (incident.getEscalationLevel() >= EscalationChain.MAX_LEVEL) return false;
        if (resolutionJustBreached) return true;
        boolean urgentUnacknowledged = incident.getPriority() != null && incident.getPriority().isUrgent()
                && incident.getAcknowledgedAt() == null && !incident.getSla().isPaused();
        if (!urgentUnacknowledged) return false;
        Duration responseTarget = Duration.ofMinutes(incident.getSla().getResponseTargetMinutes());
        Instant threshold = incident.getSla().getStartedAt()
                .plus(responseTarget.multipliedBy(incident.getEscalationLevel() + 1L))
                .plusSeconds(incident.getSla().getTotalPausedSeconds());
        return now.isAfter(threshold);
    }

    private boolean escalate(Incident incident, Instant now, boolean resolutionBreach) {
        int target = incident.getEscalationLevel() + 1;
        String reason = resolutionBreach
                ? "Resolution SLA violated"
                : "Not acknowledged within " + target + "x the response target (" + incident.getSla().getResponseTargetMinutes() + " min)";
        return escalationChain.escalate(incident, target).map(t -> {
            incident.escalateTo(t.level(), now);
            publisher.publish(new IncidentEvent.Escalated(incident, now, t, reason));
            return true;
        }).orElse(false);
    }
}
