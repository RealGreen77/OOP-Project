package com.itsm.engine.priority;

import com.itsm.domain.incident.Priority;
import org.springframework.stereotype.Component;

/**
 * Classic ITIL-style multiplicative matrix:
 * <pre>
 *   score = Impact x Urgency x BusinessCriticality      (each 1..4, so score is 1..64)
 *   P1 >= 32,  P2 >= 16,  P3 >= 6,  otherwise P4
 * </pre>
 * Rationale: multiplication means one low factor pulls priority down sharply, which matches the
 * intuition that a critical-impact incident on a low-criticality service is not a P1.
 *
 * // [Pattern: Strategy] Concrete strategy #1.
 */
@Component
public class MatrixPriorityStrategy implements PriorityStrategy {

    public static final String KEY = "matrix";

    static final int P1_THRESHOLD = 32;
    static final int P2_THRESHOLD = 16;
    static final int P3_THRESHOLD = 6;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Impact × Urgency × Criticality matrix";
    }

    @Override
    public String description() {
        return "Score = Impact × Urgency × Business Criticality (1–64). P1 ≥ 32, P2 ≥ 16, P3 ≥ 6, else P4.";
    }

    @Override
    public PriorityAssessment assess(PriorityContext ctx) {
        int score = ctx.impact().level() * ctx.urgency().level() * ctx.criticality().level();
        Priority priority = score >= P1_THRESHOLD ? Priority.P1
                : score >= P2_THRESHOLD ? Priority.P2
                : score >= P3_THRESHOLD ? Priority.P3
                : Priority.P4;
        String rationale = String.format("Matrix: impact %s(%d) × urgency %s(%d) × criticality %s(%d) = %d → %s",
                ctx.impact(), ctx.impact().level(), ctx.urgency(), ctx.urgency().level(),
                ctx.criticality(), ctx.criticality().level(), score, priority.display());
        return new PriorityAssessment(priority, score, rationale, KEY);
    }
}
