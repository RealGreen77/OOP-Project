package com.itsm.engine.assignment;

import com.itsm.domain.incident.Incident;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Only technicians skilled in the incident's category are eligible; among them the least-loaded wins.
 * // [Pattern: Strategy] Concrete assignment strategy: Skill-Based.
 */
@Component
public class SkillBasedAssignmentStrategy extends AbstractScoringAssignmentStrategy {

    public static final String KEY = "skill-based";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Skill-based";
    }

    @Override
    public String description() {
        return "Only technicians skilled in the incident category are considered; the least-loaded one is chosen.";
    }

    @Override
    protected boolean isEligible(Incident incident, TechnicianCandidate candidate) {
        return candidate.technician().hasSkill(incident.getCategory());
    }

    @Override
    protected TechnicianRecommendation evaluate(Incident incident, TechnicianCandidate c) {
        double score = 1.0 - 0.5 * Math.min(1.0, c.loadRatio());
        return recommendation(c, score, List.of(
                "Skilled in " + incident.getCategory().getName(),
                "Workload " + c.activeIncidents() + "/" + c.technician().getMaxConcurrentIncidents()));
    }
}
