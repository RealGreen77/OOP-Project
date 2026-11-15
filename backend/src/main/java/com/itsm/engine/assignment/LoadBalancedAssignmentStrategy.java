package com.itsm.engine.assignment;

import com.itsm.domain.incident.Incident;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Spreads work evenly: the technician with the lowest load ratio wins; skill is only a small tie-breaker.
 * // [Pattern: Strategy] Concrete assignment strategy: Load-Based.
 */
@Component
public class LoadBalancedAssignmentStrategy extends AbstractScoringAssignmentStrategy {

    public static final String KEY = "load-balanced";
    static final double SKILL_BONUS = 0.1;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Load-balanced";
    }

    @Override
    public String description() {
        return "Chooses the technician with the lowest current workload ratio; matching skill is a small bonus.";
    }

    @Override
    protected TechnicianRecommendation evaluate(Incident incident, TechnicianCandidate c) {
        boolean skilled = c.technician().hasSkill(incident.getCategory());
        double score = (1.0 - Math.min(1.0, c.loadRatio())) + (skilled ? SKILL_BONUS : 0);
        List<String> reasons = new ArrayList<>();
        reasons.add(String.format("Load %.0f%% (%d/%d)", c.loadRatio() * 100, c.activeIncidents(),
                c.technician().getMaxConcurrentIncidents()));
        if (skilled) reasons.add("Skilled in " + incident.getCategory().getName());
        return recommendation(c, score, reasons);
    }
}
