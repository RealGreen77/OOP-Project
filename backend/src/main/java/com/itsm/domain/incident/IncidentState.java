package com.itsm.domain.incident;

import com.itsm.domain.catalog.Category;
import com.itsm.domain.user.Technician;
import com.itsm.exception.InvalidStateTransitionException;

import java.time.Instant;
import java.util.Set;

/**
 * Behaviour of an incident in one lifecycle status.
 *
 * // [Pattern: State] Each concrete subclass permits only the transitions that make sense from its status;
 * //                 every other operation falls through to the default implementation here, which throws
 * //                 {@link InvalidStateTransitionException}. Illegal jumps such as NEW -> CLOSED are
 * //                 therefore impossible by construction rather than guarded by scattered if-statements.
 * // [OOP: Abstraction] Callers only see this abstract type; concrete states are package-private.
 * // [OOP: Polymorphism] {@code incident.currentState().resolve(...)} dispatches to the right class at runtime.
 */
public abstract class IncidentState {

    /** The persisted status this state represents. */
    public abstract IncidentStatus status();

    /** The operations permitted in this state. */
    public abstract Set<IncidentAction> allowedActions();

    public final boolean allows(IncidentAction action) {
        return allowedActions().contains(action);
    }

    // ---------------------------------------------------------------- transitions (reject by default)

    void classify(Incident incident, Category category, Instant at) {
        throw reject(IncidentAction.CLASSIFY);
    }

    void assign(Incident incident, Technician technician, Instant at) {
        throw reject(IncidentAction.ASSIGN);
    }

    void acknowledge(Incident incident, Instant at) {
        throw reject(IncidentAction.ACKNOWLEDGE);
    }

    void startWork(Incident incident, Instant at) {
        throw reject(IncidentAction.START_WORK);
    }

    void requestInfo(Incident incident, Instant at) {
        throw reject(IncidentAction.REQUEST_INFO);
    }

    void provideInfo(Incident incident, Instant at) {
        throw reject(IncidentAction.PROVIDE_INFO);
    }

    void resolve(Incident incident, Resolution resolution, Instant at) {
        throw reject(IncidentAction.RESOLVE);
    }

    void verify(Incident incident, Instant at) {
        throw reject(IncidentAction.VERIFY);
    }

    void reopen(Incident incident, Instant at) {
        throw reject(IncidentAction.REOPEN);
    }

    void close(Incident incident, ClosureCode code, String notes, Instant at) {
        throw reject(IncidentAction.CLOSE);
    }

    void markDuplicate(Incident incident, Incident parent, Instant at) {
        throw reject(IncidentAction.MARK_DUPLICATE);
    }

    // ---------------------------------------------------------------- helpers shared by subclasses

    /** Shared implementation used by every state that permits closing as duplicate. */
    protected final void closeAsDuplicate(Incident incident, Incident parent, Instant at) {
        incident.linkDuplicate(parent);
        incident.applyClosure(ClosureCode.DUPLICATE, "Duplicate of " + parent.getNumber(), at);
        incident.changeStatus(IncidentStatus.CLOSED);
    }

    protected final InvalidStateTransitionException reject(IncidentAction action) {
        return new InvalidStateTransitionException(status(), action);
    }

    @Override
    public String toString() {
        return status().name();
    }
}
