package com.itsm.engine.assignment;

import com.itsm.domain.user.Technician;

/**
 * Snapshot of a technician plus the live metrics strategies need, computed once per assignment
 * so strategies stay pure (no database access) and are trivially unit-testable.
 */
public record TechnicianCandidate(Technician technician, int activeIncidents, long resolvedForService) {

    public boolean hasCapacity() {
        return technician.hasCapacity(activeIncidents);
    }

    public double loadRatio() {
        return technician.loadRatio(activeIncidents);
    }
}
