package com.itsm.events;

import com.itsm.domain.audit.AuditAction;
import com.itsm.domain.audit.AuditEntry;
import com.itsm.domain.incident.ClosureCode;
import com.itsm.domain.incident.Incident;
import com.itsm.domain.incident.IncidentStatus;
import com.itsm.domain.sla.SlaRecord;
import com.itsm.domain.user.User;
import com.itsm.service.AuditService;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Turns every incident event into an immutable audit record, producing a timeline such as:
 * <pre>
 *   10:05 INC1024 created by E102 Asha Rao
 *   10:06 Incident classified as APPLICATION
 *   10:06 Priority calculated as P1
 *   10:07 Assigned to Technician T205
 * </pre>
 * // [Pattern: Observer] Concrete observer. Marked critical: if auditing fails, the change is rolled back.
 * // [MVP: Audit] Complete incident history.
 */
@Component
public class AuditTrailListener implements IncidentEventListener {

    private final AuditService auditService;

    public AuditTrailListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public boolean critical() {
        return true;
    }

    @Override
    public void onEvent(IncidentEvent event) {
        Incident incident = event.incident();
        AuditEntry.Builder b = AuditEntry.builder(actionFor(event), AuditService.INCIDENT, event.occurredAt())
                .actor(event.actor().id(), event.actor().name(), event.actor().role())
                .entity(incident.getId(), incident.getNumber());

        // [Java 21] Exhaustive pattern matching over the sealed event hierarchy - no default branch needed.
        switch (event) {
            case IncidentEvent.Created e -> b.transition(null, IncidentStatus.NEW.name())
                    .description(incident.getNumber() + " created by " + event.actor().name()
                            + ": \"" + incident.getTitle() + "\" on " + incident.getAffectedService().getName());
            case IncidentEvent.Classified e -> b
                    .transition(e.previous() == null ? null : e.previous().getCode(), e.current().getCode())
                    .description("Incident classified as " + e.current().getCode()
                            + (e.automatic() ? String.format(" (automatic, %.0f%% confidence)", e.confidence() * 100) : ""));
            case IncidentEvent.PriorityAssigned e -> b
                    .transition(e.previous() == null ? null : e.previous().name(), e.current().name())
                    .description((e.previous() == null ? "Priority calculated as " : "Priority changed to ")
                            + e.current().display() + ". " + e.rationale());
            case IncidentEvent.SlaApplied e -> b.description(describeSla(incident.getSla()));
            case IncidentEvent.Assigned e -> b
                    .transition(code(e.previous()), code(e.assignee()))
                    .description((e.previous() == null ? "Assigned to Technician " : "Reassigned to Technician ")
                            + e.assignee().displayName()
                            + (e.automatic() ? " (automatic, strategy: " + e.strategyKey() + ")" : " (manual)"));
            case IncidentEvent.StatusChanged e -> b
                    .transition(e.from().name(), e.to().name())
                    .description(describeStatus(e, incident));
            case IncidentEvent.NoteAdded e -> b.description(
                    e.note().getType().name().replace('_', ' ').toLowerCase() + " note added"
                            + (e.note().isInternal() ? " (internal)" : ""));
            case IncidentEvent.DetailsUpdated e -> b.description("Details updated: " + e.changes());
            case IncidentEvent.SlaWarning e -> b.description(String.format(
                    "SLA warning: %.0f%% of the resolution window used", e.elapsedFraction() * 100));
            case IncidentEvent.SlaBreached e -> b.description(
                    (e.kind() == IncidentEvent.SlaBreached.Kind.RESPONSE ? "Response" : "Resolution") + " SLA violated");
            case IncidentEvent.Escalated e -> b
                    .transition("L" + (e.target().level() - 1), "L" + e.target().level())
                    .description("Escalated to " + e.target().levelName() + " ("
                            + e.target().recipients().stream().map(User::displayName).collect(Collectors.joining(", "))
                            + "): " + e.reason());
            case IncidentEvent.DuplicateSuspected e -> b.description(String.format(
                    "Possible duplicate of %s (similarity %.0f%%)", e.original().getNumber(), e.score() * 100));
            case IncidentEvent.MajorIncidentDetected e -> b.description(
                    "Major incident detected around " + e.cluster().probableRoot().getName() + ": " + e.cluster().explanation());
            case IncidentEvent.FeedbackSubmitted e -> b.description(
                    "Reporter feedback: " + incident.getFeedback().getRating() + "/5");
        }
        auditService.record(b.build());
    }

    private static AuditAction actionFor(IncidentEvent event) {
        return switch (event) {
            case IncidentEvent.Created e -> AuditAction.INCIDENT_CREATED;
            case IncidentEvent.Classified e -> AuditAction.INCIDENT_CLASSIFIED;
            case IncidentEvent.PriorityAssigned e -> e.previous() == null ? AuditAction.PRIORITY_CALCULATED : AuditAction.PRIORITY_CHANGED;
            case IncidentEvent.SlaApplied e -> AuditAction.SLA_APPLIED;
            case IncidentEvent.Assigned e -> AuditAction.INCIDENT_ASSIGNED;
            case IncidentEvent.StatusChanged e -> statusAction(e);
            case IncidentEvent.NoteAdded e -> AuditAction.NOTE_ADDED;
            case IncidentEvent.DetailsUpdated e -> AuditAction.DETAILS_UPDATED;
            case IncidentEvent.SlaWarning e -> AuditAction.SLA_WARNING;
            case IncidentEvent.SlaBreached e -> AuditAction.SLA_BREACHED;
            case IncidentEvent.Escalated e -> AuditAction.ESCALATED;
            case IncidentEvent.DuplicateSuspected e -> AuditAction.DUPLICATE_SUSPECTED;
            case IncidentEvent.MajorIncidentDetected e -> AuditAction.MAJOR_INCIDENT_DETECTED;
            case IncidentEvent.FeedbackSubmitted e -> AuditAction.FEEDBACK_SUBMITTED;
        };
    }

    private static AuditAction statusAction(IncidentEvent.StatusChanged e) {
        if (e.to() == IncidentStatus.CLOSED) {
            return e.incident().getClosureCode() == ClosureCode.DUPLICATE ? AuditAction.MARKED_DUPLICATE : AuditAction.INCIDENT_CLOSED;
        }
        if (e.to() == IncidentStatus.RESOLVED) return AuditAction.INCIDENT_RESOLVED;
        if (e.to() == IncidentStatus.PENDING_INFO) return AuditAction.INFO_REQUESTED;
        if (e.from() == IncidentStatus.PENDING_INFO) return AuditAction.INFO_PROVIDED;
        if (e.from() == IncidentStatus.RESOLVED && e.to() == IncidentStatus.IN_PROGRESS) return AuditAction.INCIDENT_REOPENED;
        return AuditAction.STATUS_CHANGED;
    }

    private static String describeStatus(IncidentEvent.StatusChanged e, Incident incident) {
        String base = switch (e.to()) {
            case ACKNOWLEDGED -> "Technician acknowledged incident";
            case IN_PROGRESS -> e.from() == IncidentStatus.PENDING_INFO ? "Reporter provided requested information"
                    : e.from() == IncidentStatus.RESOLVED ? "Incident reopened by reporter" : "Investigation started";
            case PENDING_INFO -> "Additional information requested from reporter";
            case RESOLVED -> "Resolution provided; incident marked RESOLVED";
            case VERIFIED -> "Resolution verified";
            case CLOSED -> incident.getClosureCode() == ClosureCode.DUPLICATE
                    ? "Closed as duplicate of " + (incident.getDuplicateOf() == null ? "?" : incident.getDuplicateOf().getNumber())
                    : "Incident CLOSED";
            default -> "Status changed to " + e.to();
        };
        return e.comment() == null || e.comment().isBlank() ? base : base + " - " + e.comment();
    }

    private static String describeSla(SlaRecord sla) {
        if (sla == null) return "SLA applied";
        return String.format("SLA \"%s\" applied: respond within %s, resolve within %s",
                sla.getPolicyName(), minutes(sla.getResponseTargetMinutes()), minutes(sla.getResolutionTargetMinutes()));
    }

    private static String minutes(Integer m) {
        if (m == null) return "?";
        return m < 60 ? m + " min" : (m % 60 == 0 ? (m / 60) + " h" : (m / 60) + " h " + (m % 60) + " min");
    }

    private static String code(User user) {
        return user == null ? null : user.getUserCode();
    }
}
