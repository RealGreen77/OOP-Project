package com.itsm.engine.assignment;

import com.itsm.domain.incident.Incident;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Skeleton shared by score-based assignment strategies.
 *
 * // [Pattern: Template Method] {@link #rank} fixes the algorithm (capacity filter -> eligibility -> score -> order);
 * //                            subclasses supply only {@link #isEligible} and {@link #evaluate}.
 * // [OOP: Abstraction] Concrete strategies cannot accidentally skip the capacity check.
 * // [Collections] PriorityQueue orders candidates by score in O(n log n) with a deterministic tie-break.
 */
public abstract class AbstractScoringAssignmentStrategy implements AssignmentStrategy {

    /** Highest score first; ties broken by lighter workload, then by user code for determinism. */
    protected static final Comparator<TechnicianRecommendation> BEST_FIRST =
            Comparator.comparingDouble(TechnicianRecommendation::score).reversed()
                    .thenComparingInt(TechnicianRecommendation::activeIncidents)
                    .thenComparing(r -> r.technician().getUserCode());

    @Override
    public final List<TechnicianRecommendation> rank(Incident incident, List<TechnicianCandidate> candidates) {
        PriorityQueue<TechnicianRecommendation> queue = new PriorityQueue<>(BEST_FIRST);
        for (TechnicianCandidate candidate : candidates) {
            if (candidate.hasCapacity() && isEligible(incident, candidate)) {
                queue.add(evaluate(incident, candidate));
            }
        }
        List<TechnicianRecommendation> ordered = new ArrayList<>(queue.size());
        while (!queue.isEmpty()) {
            ordered.add(queue.poll());
        }
        return ordered;
    }

    /** Hook: may this candidate be considered at all? Default: yes. */
    protected boolean isEligible(Incident incident, TechnicianCandidate candidate) {
        return true;
    }

    /** Hook: score one eligible candidate (higher is better) with reasons. */
    protected abstract TechnicianRecommendation evaluate(Incident incident, TechnicianCandidate candidate);

    protected static TechnicianRecommendation recommendation(TechnicianCandidate c, double score, List<String> reasons) {
        return new TechnicianRecommendation(c.technician(), Math.round(score * 1000.0) / 1000.0,
                c.activeIncidents(), c.technician().getMaxConcurrentIncidents(), List.copyOf(reasons));
    }
}
