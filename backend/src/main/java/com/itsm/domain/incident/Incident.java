package com.itsm.domain.incident;

import com.itsm.domain.catalog.Category;
import com.itsm.domain.catalog.ITService;
import com.itsm.domain.common.BaseEntity;
import com.itsm.domain.sla.SlaPolicy;
import com.itsm.domain.sla.SlaRecord;
import com.itsm.domain.sla.SlaStatus;
import com.itsm.domain.user.Technician;
import com.itsm.domain.user.User;
import com.itsm.exception.BusinessRuleViolationException;
import com.itsm.exception.InvalidInputException;
import com.itsm.exception.InvalidStateTransitionException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The central aggregate: an IT incident and its complete lifecycle.
 *
 * // [OOP: Encapsulation] No public setters for status, assignment or timestamps. All lifecycle changes go
 * //                    through intention-revealing methods that delegate to the current {@link IncidentState};
 * //                    the raw mutators are package-private and callable only by the state classes.
 * // [Pattern: State] {@link #currentState()} returns the behaviour object for the persisted status.
 * // [OOP: Composition] SlaRecord, Resolution, Feedback and the note list are owned parts of the incident.
 * // [OOP: Association] Reporter (User), assignee (Technician), Category and ITService are associations.
 * // [Concurrency] @Version gives optimistic locking so two technicians cannot overwrite each other.
 */
@Entity
@Table(name = "incidents")
public class Incident extends BaseEntity {

    public static final String NUMBER_PREFIX = "INC";

    @Column(name = "incident_number", unique = true, length = 20)
    private String number;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ITService affectedService;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Impact impact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Urgency urgency;

    @Column(name = "affected_users", nullable = false)
    private int affectedUsers = 1;

    @Enumerated(EnumType.STRING)
    @Column(length = 4)
    private Priority priority;

    @Column(name = "priority_score")
    private Double priorityScore;

    @Column(name = "priority_rationale", length = 500)
    private String priorityRationale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status = IncidentStatus.NEW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private Technician assignedTo;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Embedded
    private SlaRecord sla;

    @Embedded
    private Resolution resolution;

    @Enumerated(EnumType.STRING)
    @Column(name = "closure_code", length = 30)
    private ClosureCode closureCode;

    @Column(name = "closure_notes", length = 1000)
    private String closureNotes;

    @Embedded
    private Feedback feedback;

    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel;

    @Column(name = "last_escalated_at")
    private Instant lastEscalatedAt;

    @Column(name = "reopen_count", nullable = false)
    private int reopenCount;

    /** Confirmed duplicate parent (set when closed as DUPLICATE). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_of_id")
    private Incident duplicateOf;

    /** System-suggested possible duplicate, pending human confirmation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suspected_duplicate_of_id")
    private Incident suspectedDuplicateOf;

    @Column(name = "suspected_duplicate_score")
    private Double suspectedDuplicateScore;

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<IncidentNote> notes = new ArrayList<>();

    @Version
    private long version;

    /** Required by JPA. */
    protected Incident() {
    }

    /** Package-private: incidents are created through {@link IncidentFactory}. // [Pattern: Factory] */
    Incident(String title, String description, User reporter, ITService service,
             Impact impact, Urgency urgency, int affectedUsers, Instant reportedAt) {
        this.reporter = reporter;
        this.reportedAt = reportedAt;
        this.status = IncidentStatus.NEW;
        applyDetails(title, description, service, impact, urgency, affectedUsers);
        initialiseCreatedAt(reportedAt);
    }

    // ================================================================ State pattern delegation

    /** Behaviour object for the current status. // [OOP: Polymorphism] */
    public IncidentState currentState() {
        return IncidentStateFactory.forStatus(status);
    }

    /** Actions valid right now (used by the UI to show only legal buttons). */
    public Set<IncidentAction> allowedActions() {
        return currentState().allowedActions();
    }

    public boolean allows(IncidentAction action) {
        return currentState().allows(action);
    }

    public void classify(Category newCategory, Instant at) {
        currentState().classify(this, newCategory, at);
    }

    public void assignTo(Technician technician, Instant at) {
        currentState().assign(this, technician, at);
    }

    public void acknowledge(Instant at) {
        currentState().acknowledge(this, at);
    }

    public void startWork(Instant at) {
        currentState().startWork(this, at);
    }

    public void requestInformation(Instant at) {
        currentState().requestInfo(this, at);
    }

    public void provideInformation(Instant at) {
        currentState().provideInfo(this, at);
    }

    public void resolve(Resolution resolutionDetails, Instant at) {
        currentState().resolve(this, resolutionDetails, at);
    }

    public void verify(Instant at) {
        currentState().verify(this, at);
    }

    public void reopen(Instant at) {
        currentState().reopen(this, at);
    }

    public void close(ClosureCode code, String notesText, Instant at) {
        currentState().close(this, code, notesText, at);
    }

    public void markDuplicateOf(Incident parent, Instant at) {
        if (parent == null || parent == this || (parent.getId() != null && parent.getId().equals(getId()))) {
            throw new InvalidInputException("An incident cannot be a duplicate of itself");
        }
        if (parent.getDuplicateOf() != null) {
            throw new BusinessRuleViolationException("DUPLICATE_CHAIN",
                    "Parent incident is itself a duplicate of " + parent.getDuplicateOf().getNumber());
        }
        currentState().markDuplicate(this, parent, at);
    }

    // ================================================================ non-lifecycle behaviour

    /** Updates reported details. Allowed while the incident is still active. */
    public void updateDetails(String newTitle, String newDescription, ITService service,
                              Impact newImpact, Urgency newUrgency, int newAffectedUsers) {
        guard(IncidentAction.UPDATE_DETAILS);
        applyDetails(newTitle, newDescription, service, newImpact, newUrgency, newAffectedUsers);
    }

    public IncidentNote addNote(User author, NoteType type, String content, boolean internal, Instant at) {
        guard(IncidentAction.ADD_NOTE);
        IncidentNote note = new IncidentNote(this, author, type, content, internal, at);
        notes.add(note);
        return note;
    }

    public void submitFeedback(Feedback newFeedback) {
        guard(IncidentAction.FEEDBACK);
        if (closureCode == ClosureCode.DUPLICATE) {
            throw new BusinessRuleViolationException("FEEDBACK_NOT_ALLOWED", "Feedback is not collected for duplicates");
        }
        if (this.feedback != null && this.feedback.getRating() != null) {
            throw new BusinessRuleViolationException("FEEDBACK_ALREADY_SUBMITTED", "Feedback was already submitted");
        }
        this.feedback = newFeedback;
    }

    /** Stores the outcome of a priority strategy. */
    public void applyPriority(Priority newPriority, double score, String rationale) {
        if (newPriority == null) throw new InvalidInputException("priority must not be null");
        this.priority = newPriority;
        this.priorityScore = Math.round(score * 100.0) / 100.0;
        this.priorityRationale = rationale == null ? null
                : rationale.substring(0, Math.min(rationale.length(), 500));
    }

    /** Starts (or re-targets after a priority change) the SLA clock. */
    public void applySlaPolicy(SlaPolicy policy) {
        this.sla = (sla == null || sla.getStartedAt() == null) ? SlaRecord.start(policy, reportedAt) : sla.retarget(policy);
    }

    /** Raises the escalation level (never lowers it). */
    public void escalateTo(int level, Instant at) {
        if (level <= escalationLevel) {
            throw new BusinessRuleViolationException("ESCALATION_LEVEL", "Incident already at escalation level " + escalationLevel);
        }
        escalationLevel = level;
        lastEscalatedAt = at;
    }

    // ---------------------------------------------------------------- SLA checks (used by the SLA monitor)

    /** @return true if a response-SLA breach was newly detected. */
    public boolean checkResponseBreach(Instant now) {
        return hasSla() && status.isActive() && sla.flagResponseBreachIfDue(now);
    }

    /** @return true if a resolution-SLA breach was newly detected. */
    public boolean checkResolutionBreach(Instant now) {
        return hasSla() && status.isActive() && sla.flagResolutionBreachIfDue(now);
    }

    /** @return true if the one-time "deadline approaching" warning should be raised now. */
    public boolean checkSlaWarning(Instant now) {
        return hasSla() && status.isActive() && sla.shouldWarn(now);
    }

    public boolean hasSla() {
        return sla != null && sla.getStartedAt() != null;
    }

    public void flagSuspectedDuplicate(Incident candidate, double score) {
        this.suspectedDuplicateOf = candidate;
        this.suspectedDuplicateScore = Math.round(score * 1000.0) / 1000.0;
    }

    /** Assigns the human-readable number once the database id is known (e.g. INC1024). */
    public void assignNumber(long sequence) {
        if (number != null) throw new IllegalStateException("Incident number already assigned: " + number);
        this.number = NUMBER_PREFIX + sequence;
    }

    public SlaStatus slaStatus(Instant now) {
        return sla == null || sla.getStartedAt() == null ? SlaStatus.NOT_APPLICABLE : sla.status(now, resolvedAt);
    }

    public boolean isAssignedTo(User user) {
        return assignedTo != null && user != null && assignedTo.getId() != null && assignedTo.getId().equals(user.getId());
    }

    public boolean isReportedBy(User user) {
        return reporter != null && user != null && reporter.getId() != null && reporter.getId().equals(user.getId());
    }

    // ================================================================ package-private mutators (State classes only)

    void changeStatus(IncidentStatus newStatus) {
        this.status = newStatus;
    }

    void applyCategory(Category newCategory) {
        if (newCategory == null) throw new InvalidInputException("category must not be null");
        if (!newCategory.isActive()) throw new InvalidInputException("Category " + newCategory.getCode() + " is inactive");
        this.category = newCategory;
    }

    void applyAssignment(Technician technician, Instant at) {
        if (technician == null) throw new InvalidInputException("technician must not be null");
        this.assignedTo = technician;
        this.assignedAt = at;
    }

    void markAcknowledged(Instant at) {
        if (acknowledgedAt == null) acknowledgedAt = at;
        if (sla != null && sla.getStartedAt() != null) sla.recordResponse(at);
    }

    void pauseSla(Instant at) {
        if (sla != null && sla.getStartedAt() != null) sla.pause(at);
    }

    void resumeSla(Instant at) {
        if (sla != null && sla.getStartedAt() != null) sla.resume(at);
    }

    void applyResolution(Resolution details, Instant at) {
        if (details == null) throw new InvalidInputException("resolution must not be null");
        this.resolution = details;
        this.resolvedAt = at;
        if (sla != null && sla.getStartedAt() != null) sla.finaliseResolution(at);
    }

    void markVerified(Instant at) {
        this.verifiedAt = at;
    }

    void markReopened() {
        this.resolvedAt = null;
        this.verifiedAt = null;
        this.reopenCount++;
    }

    void applyClosure(ClosureCode code, String closureText, Instant at) {
        this.closureCode = code == null ? ClosureCode.RESOLVED : code;
        this.closureNotes = closureText;
        this.closedAt = at;
    }

    void linkDuplicate(Incident parent) {
        this.duplicateOf = parent;
    }

    private void guard(IncidentAction action) {
        if (!allows(action)) throw new InvalidStateTransitionException(status, action);
    }

    private void applyDetails(String newTitle, String newDescription, ITService service,
                              Impact newImpact, Urgency newUrgency, int newAffectedUsers) {
        if (newTitle == null || newTitle.trim().length() < 5 || newTitle.length() > 200) {
            throw new InvalidInputException("Title must be between 5 and 200 characters");
        }
        if (newDescription == null || newDescription.trim().length() < 10 || newDescription.length() > 4000) {
            throw new InvalidInputException("Description must be between 10 and 4000 characters");
        }
        if (service == null) throw new InvalidInputException("Affected service is required");
        if (newImpact == null || newUrgency == null) throw new InvalidInputException("Impact and urgency are required");
        if (newAffectedUsers < 1 || newAffectedUsers > 1_000_000) {
            throw new InvalidInputException("Affected users must be between 1 and 1,000,000");
        }
        this.title = newTitle.trim();
        this.description = newDescription.trim();
        this.affectedService = service;
        this.impact = newImpact;
        this.urgency = newUrgency;
        this.affectedUsers = newAffectedUsers;
    }

    // ================================================================ accessors

    public String getNumber() { return number; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public User getReporter() { return reporter; }
    public Category getCategory() { return category; }
    public ITService getAffectedService() { return affectedService; }
    public Impact getImpact() { return impact; }
    public Urgency getUrgency() { return urgency; }
    public int getAffectedUsers() { return affectedUsers; }
    public Priority getPriority() { return priority; }
    public Double getPriorityScore() { return priorityScore; }
    public String getPriorityRationale() { return priorityRationale; }
    public IncidentStatus getStatus() { return status; }
    public Technician getAssignedTo() { return assignedTo; }
    public Instant getReportedAt() { return reportedAt; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getClosedAt() { return closedAt; }
    public SlaRecord getSla() { return sla; }
    public Resolution getResolution() { return resolution; }
    public ClosureCode getClosureCode() { return closureCode; }
    public String getClosureNotes() { return closureNotes; }
    public Feedback getFeedback() { return feedback; }
    public int getEscalationLevel() { return escalationLevel; }
    public Instant getLastEscalatedAt() { return lastEscalatedAt; }
    public int getReopenCount() { return reopenCount; }
    public Incident getDuplicateOf() { return duplicateOf; }
    public Incident getSuspectedDuplicateOf() { return suspectedDuplicateOf; }
    public Double getSuspectedDuplicateScore() { return suspectedDuplicateScore; }
    public List<IncidentNote> getNotes() { return Collections.unmodifiableList(notes); }
    public long getVersion() { return version; }

    /** Text used by similarity-based intelligent features. */
    public String searchableText() {
        return title + " " + description;
    }
}
