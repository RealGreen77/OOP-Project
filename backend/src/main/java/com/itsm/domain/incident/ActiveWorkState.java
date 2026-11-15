package com.itsm.domain.incident;

import com.itsm.domain.catalog.Category;
import com.itsm.domain.user.Technician;

import java.time.Instant;

/**
 * Behaviour shared by every state in which a technician owns the incident
 * (ASSIGNED, ACKNOWLEDGED, IN_PROGRESS): re-assignment, re-classification and duplicate closure.
 *
 * // [OOP: Inheritance] An intermediate abstract class removes duplication between related states.
 */
abstract class ActiveWorkState extends IncidentState {

    /** Re-assigning sends the incident back to ASSIGNED so the new owner must acknowledge it. */
    @Override
    void assign(Incident incident, Technician technician, Instant at) {
        incident.applyAssignment(technician, at);
        incident.changeStatus(IncidentStatus.ASSIGNED);
    }

    @Override
    void classify(Incident incident, Category category, Instant at) {
        incident.applyCategory(category);
    }

    @Override
    void markDuplicate(Incident incident, Incident parent, Instant at) {
        closeAsDuplicate(incident, parent, at);
    }
}
