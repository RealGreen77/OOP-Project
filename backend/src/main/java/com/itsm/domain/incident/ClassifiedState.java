package com.itsm.domain.incident;

import com.itsm.domain.catalog.Category;
import com.itsm.domain.user.Technician;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** CLASSIFIED: category, priority and SLA known; waiting for a technician. */
final class ClassifiedState extends IncidentState {

    private static final Set<IncidentAction> ALLOWED = EnumSet.of(
            IncidentAction.CLASSIFY, IncidentAction.ASSIGN, IncidentAction.MARK_DUPLICATE,
            IncidentAction.UPDATE_DETAILS, IncidentAction.ADD_NOTE);

    @Override
    public IncidentStatus status() {
        return IncidentStatus.CLASSIFIED;
    }

    @Override
    public Set<IncidentAction> allowedActions() {
        return ALLOWED;
    }

    /** Re-classification keeps the status. */
    @Override
    void classify(Incident incident, Category category, Instant at) {
        incident.applyCategory(category);
    }

    @Override
    void assign(Incident incident, Technician technician, Instant at) {
        incident.applyAssignment(technician, at);
        incident.changeStatus(IncidentStatus.ASSIGNED);
    }

    @Override
    void markDuplicate(Incident incident, Incident parent, Instant at) {
        closeAsDuplicate(incident, parent, at);
    }
}
