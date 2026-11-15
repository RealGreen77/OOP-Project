package com.itsm.events;

import com.itsm.domain.catalog.Category;
import com.itsm.domain.incident.Incident;
import com.itsm.domain.incident.IncidentNote;
import com.itsm.domain.incident.IncidentStatus;
import com.itsm.domain.incident.Priority;
import com.itsm.domain.user.Technician;
import com.itsm.engine.escalation.EscalationTarget;
import com.itsm.engine.intelligence.CorrelationCluster;

import java.time.Instant;

/**
 * Something significant happened to an incident.
 *
 * // [Pattern: Observer] Events are published by the service layer and consumed by independent observers
 * //                    (audit trail, notifications, live dashboard). Publishers never know who listens.
 * // [OOP: Inheritance + Polymorphism] A sealed hierarchy: the compiler knows every event type, so listeners
 * //                    can use exhaustive pattern-matching switches (Java 21) with no default branch.
 */
public abstract sealed class IncidentEvent {

    private final Incident incident;
    private final ActorRef actor;
    private final Instant occurredAt;

    protected IncidentEvent(Incident incident, ActorRef actor, Instant occurredAt) {
        this.incident = incident;
        this.actor = actor == null ? ActorRef.SYSTEM : actor;
        this.occurredAt = occurredAt;
    }

    /** Stable event name used in notifications and the live feed. */
    public abstract String eventType();

    public Incident incident() { return incident; }
    public ActorRef actor() { return actor; }
    public Instant occurredAt() { return occurredAt; }

    // ============================================================ concrete events

    public static final class Created extends IncidentEvent {
        public Created(Incident incident, ActorRef actor, Instant at) { super(incident, actor, at); }
        @Override public String eventType() { return "INCIDENT_CREATED"; }
    }

    public static final class Classified extends IncidentEvent {
        private final Category previous;
        private final Category current;
        private final boolean automatic;
        private final double confidence;

        public Classified(Incident incident, ActorRef actor, Instant at, Category previous, Category current,
                          boolean automatic, double confidence) {
            super(incident, actor, at);
            this.previous = previous;
            this.current = current;
            this.automatic = automatic;
            this.confidence = confidence;
        }
        @Override public String eventType() { return "INCIDENT_CLASSIFIED"; }
        public Category previous() { return previous; }
        public Category current() { return current; }
        public boolean automatic() { return automatic; }
        public double confidence() { return confidence; }
    }

    public static final class PriorityAssigned extends IncidentEvent {
        private final Priority previous;
        private final Priority current;
        private final String rationale;

        public PriorityAssigned(Incident incident, ActorRef actor, Instant at, Priority previous, Priority current,
                                String rationale) {
            super(incident, actor, at);
            this.previous = previous;
            this.current = current;
            this.rationale = rationale;
        }
        @Override public String eventType() { return previous == null ? "PRIORITY_CALCULATED" : "PRIORITY_CHANGED"; }
        public Priority previous() { return previous; }
        public Priority current() { return current; }
        public String rationale() { return rationale; }
        public boolean isChange() { return previous != null && previous != current; }
    }

    public static final class SlaApplied extends IncidentEvent {
        public SlaApplied(Incident incident, ActorRef actor, Instant at) { super(incident, actor, at); }
        @Override public String eventType() { return "SLA_APPLIED"; }
    }

    public static final class Assigned extends IncidentEvent {
        private final Technician previous;
        private final Technician assignee;
        private final String strategyKey;
        private final boolean automatic;

        public Assigned(Incident incident, ActorRef actor, Instant at, Technician previous, Technician assignee,
                        String strategyKey, boolean automatic) {
            super(incident, actor, at);
            this.previous = previous;
            this.assignee = assignee;
            this.strategyKey = strategyKey;
            this.automatic = automatic;
        }
        @Override public String eventType() { return previous == null ? "INCIDENT_ASSIGNED" : "INCIDENT_REASSIGNED"; }
        public Technician previous() { return previous; }
        public Technician assignee() { return assignee; }
        public String strategyKey() { return strategyKey; }
        public boolean automatic() { return automatic; }
    }

    public static final class StatusChanged extends IncidentEvent {
        private final IncidentStatus from;
        private final IncidentStatus to;
        private final String comment;

        public StatusChanged(Incident incident, ActorRef actor, Instant at, IncidentStatus from, IncidentStatus to,
                             String comment) {
            super(incident, actor, at);
            this.from = from;
            this.to = to;
            this.comment = comment;
        }
        @Override public String eventType() {
            return switch (to) {
                case RESOLVED -> "INCIDENT_RESOLVED";
                case CLOSED -> "INCIDENT_CLOSED";
                case PENDING_INFO -> "INFO_REQUESTED";
                default -> from == IncidentStatus.RESOLVED ? "INCIDENT_REOPENED" : "STATUS_CHANGED";
            };
        }
        public IncidentStatus from() { return from; }
        public IncidentStatus to() { return to; }
        public String comment() { return comment; }
    }

    public static final class NoteAdded extends IncidentEvent {
        private final IncidentNote note;

        public NoteAdded(Incident incident, ActorRef actor, Instant at, IncidentNote note) {
            super(incident, actor, at);
            this.note = note;
        }
        @Override public String eventType() { return "NOTE_ADDED"; }
        public IncidentNote note() { return note; }
    }

    public static final class DetailsUpdated extends IncidentEvent {
        private final String changes;

        public DetailsUpdated(Incident incident, ActorRef actor, Instant at, String changes) {
            super(incident, actor, at);
            this.changes = changes;
        }
        @Override public String eventType() { return "DETAILS_UPDATED"; }
        public String changes() { return changes; }
    }

    public static final class SlaWarning extends IncidentEvent {
        private final double elapsedFraction;

        public SlaWarning(Incident incident, Instant at, double elapsedFraction) {
            super(incident, ActorRef.SYSTEM, at);
            this.elapsedFraction = elapsedFraction;
        }
        @Override public String eventType() { return "SLA_WARNING"; }
        public double elapsedFraction() { return elapsedFraction; }
    }

    public static final class SlaBreached extends IncidentEvent {
        public enum Kind { RESPONSE, RESOLUTION }

        private final Kind kind;

        public SlaBreached(Incident incident, Instant at, Kind kind) {
            super(incident, ActorRef.SYSTEM, at);
            this.kind = kind;
        }
        @Override public String eventType() { return "SLA_BREACHED"; }
        public Kind kind() { return kind; }
    }

    public static final class Escalated extends IncidentEvent {
        private final EscalationTarget target;
        private final String reason;

        public Escalated(Incident incident, Instant at, EscalationTarget target, String reason) {
            super(incident, ActorRef.SYSTEM, at);
            this.target = target;
            this.reason = reason;
        }
        @Override public String eventType() { return "INCIDENT_ESCALATED"; }
        public EscalationTarget target() { return target; }
        public String reason() { return reason; }
    }

    public static final class DuplicateSuspected extends IncidentEvent {
        private final Incident original;
        private final double score;

        public DuplicateSuspected(Incident incident, Instant at, Incident original, double score) {
            super(incident, ActorRef.SYSTEM, at);
            this.original = original;
            this.score = score;
        }
        @Override public String eventType() { return "DUPLICATE_SUSPECTED"; }
        public Incident original() { return original; }
        public double score() { return score; }
    }

    public static final class MajorIncidentDetected extends IncidentEvent {
        private final CorrelationCluster cluster;

        public MajorIncidentDetected(Incident trigger, Instant at, CorrelationCluster cluster) {
            super(trigger, ActorRef.SYSTEM, at);
            this.cluster = cluster;
        }
        @Override public String eventType() { return "MAJOR_INCIDENT_DETECTED"; }
        public CorrelationCluster cluster() { return cluster; }
    }

    public static final class FeedbackSubmitted extends IncidentEvent {
        public FeedbackSubmitted(Incident incident, ActorRef actor, Instant at) { super(incident, actor, at); }
        @Override public String eventType() { return "FEEDBACK_SUBMITTED"; }
    }
}
