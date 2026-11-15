package com.itsm.engine.assignment;

import com.itsm.domain.incident.Incident;
import com.itsm.domain.incident.Priority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-factor recommendation (0..1):
 * <pre>
 *   0.40 x skill match (category)          0.25 x spare capacity (1 - load ratio)
 *   0.15 x experience (years / 10, capped)  0.10 x past success on this service (resolved / 5, capped)
 *   0.10 x priority fit (P1/P2 prefer 5+ years experience; P3/P4 prefer juniors to keep seniors free)
 * </pre>
 * // [Pattern: Strategy] Concrete assignment strategy: Experience/Skill/Load weighted scoring.
 * // [Intelligent Feature D] Smart Technician Recommendation - every score comes with reasons.
 */
@Component
public class SmartScoringAssignmentStrategy extends AbstractScoringAssignmentStrategy {

    public static final String KEY = "smart";

    static final double W_SKILL = 0.40;
    static final double W_CAPACITY = 0.25;
    static final double W_EXPERIENCE = 0.15;
    static final double W_SERVICE_HISTORY = 0.10;
    static final double W_PRIORITY_FIT = 0.10;
    static final int SENIOR_YEARS = 5;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Smart recommendation (multi-factor)";
    }

    @Override
    public String description() {
        return "Weighted score of skill (40%), spare capacity (25%), experience (15%), "
                + "past success on the service (10%) and priority/seniority fit (10%).";
    }

    @Override
    protected TechnicianRecommendation evaluate(Incident incident, TechnicianCandidate c) {
        List<String> reasons = new ArrayList<>();
        var tech = c.technician();

        double skill = tech.hasSkill(incident.getCategory()) ? 1.0 : 0.0;
        if (skill > 0) reasons.add("Skilled in " + incident.getCategory().getName());
        else reasons.add("No direct skill match");

        double capacity = 1.0 - Math.min(1.0, c.loadRatio());
        reasons.add(String.format("%d/%d active incidents", c.activeIncidents(), tech.getMaxConcurrentIncidents()));

        double experience = Math.min(1.0, tech.getExperienceYears() / 10.0);
        reasons.add(tech.getExperienceYears() + " years experience");

        double serviceHistory = Math.min(1.0, c.resolvedForService() / 5.0);
        if (tech.hasExpertiseIn(incident.getAffectedService())) {
            serviceHistory = Math.max(serviceHistory, 0.6);
            reasons.add("Listed expert for " + incident.getAffectedService().getName());
        }
        if (c.resolvedForService() > 0) {
            reasons.add("Resolved " + c.resolvedForService() + " past incident(s) on this service");
        }

        boolean senior = tech.getExperienceYears() >= SENIOR_YEARS;
        boolean urgent = incident.getPriority() != null && incident.getPriority().isUrgent();
        double priorityFit = urgent == senior ? 1.0 : 0.3;
        if (urgent && senior) reasons.add("Senior engineer for " + incident.getPriority() + " incident");
        if (!urgent && !senior && incident.getPriority() != null && incident.getPriority() != Priority.P1) {
            reasons.add("Keeps senior engineers free for critical work");
        }

        double score = W_SKILL * skill + W_CAPACITY * capacity + W_EXPERIENCE * experience
                + W_SERVICE_HISTORY * serviceHistory + W_PRIORITY_FIT * priorityFit;
        return recommendation(c, score, reasons);
    }
}
