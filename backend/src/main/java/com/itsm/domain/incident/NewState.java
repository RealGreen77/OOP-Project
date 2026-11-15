package com.itsm.domain.incident;

import com.itsm.domain.catalog.Category;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** NEW: just reported. Can only be classified (or closed as a duplicate). */
final class NewState extends IncidentState {

    private static final Set<IncidentAction> ALLOWED = EnumSet.of(
            IncidentAction.CLASSIFY, IncidentAction.MARK_DUPLICATE,
            IncidentAction.UPDATE_DETAILS, IncidentAction.ADD_NOTE);

    @Override
    public IncidentStatus status() {
        return IncidentStatus.NEW;
    }

    @Override
    public Set<IncidentAction> allowedActions() {
        return ALLOWED;
    }

    @Override
    void classify(Incident incident, Category category, Instant at) {
        incident.applyCategory(category);
        incident.changeStatus(IncidentStatus.CLASSIFIED);
    }

    @Override
    void markDuplicate(Incident incident, Incident parent, Instant at) {
        closeAsDuplicate(incident, parent, at);
    }
}
