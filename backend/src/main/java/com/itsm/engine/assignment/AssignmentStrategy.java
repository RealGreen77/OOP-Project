package com.itsm.engine.assignment;

import com.itsm.domain.incident.Incident;
import com.itsm.engine.NamedStrategy;

import java.util.List;
import java.util.Optional;

/**
 * Chooses which technician should handle an incident.
 *
 * // [Pattern: Strategy] Four interchangeable implementations; the active one is chosen by an administrator
 * //                    at runtime and persisted, so the assignment approach changes without redesign.
 * // [MVP: Technician Assignment] At least two assignment strategies.
 */
public interface AssignmentStrategy extends NamedStrategy {

    /** All eligible technicians ordered best-first (used for recommendations; has no side effects). */
    List<TechnicianRecommendation> rank(Incident incident, List<TechnicianCandidate> candidates);

    /** The technician to assign. Strategies with internal state (round robin) override this. */
    default Optional<TechnicianRecommendation> select(Incident incident, List<TechnicianCandidate> candidates) {
        return rank(incident, candidates).stream().findFirst();
    }
}
