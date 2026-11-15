package com.itsm.engine.priority;

import com.itsm.domain.incident.Priority;
import com.itsm.repository.IncidentRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Weighted additive scoring with a historical-recurrence boost (0..100 scale):
 * <pre>
 *   base  = 35*impact + 30*urgency + 20*criticality + 15*userReach        (each factor normalised 0..1)
 *   userReach = log10(affectedUsers) / log10(1000), capped at 1
 *   boost = +8 if the same service had >= 3 incidents in the last 24h (recurring/systemic problem)
 *   P1 >= 75,  P2 >= 55,  P3 >= 35,  otherwise P4
 * </pre>
 * Unlike the matrix, this strategy also uses the number of affected users and historical data.
 *
 * // [Pattern: Strategy] Concrete strategy #2 (rule-based + historical-data-based prioritisation).
 */
@Component
public class WeightedScoringPriorityStrategy implements PriorityStrategy {

    public static final String KEY = "weighted";

    static final double W_IMPACT = 35;
    static final double W_URGENCY = 30;
    static final double W_CRITICALITY = 20;
    static final double W_REACH = 15;
    static final double RECURRENCE_BOOST = 8;
    static final int RECURRENCE_THRESHOLD = 3;

    private final IncidentRepository incidentRepository;
    private final Clock clock;

    public WeightedScoringPriorityStrategy(IncidentRepository incidentRepository, Clock clock) {
        this.incidentRepository = incidentRepository;
        this.clock = clock;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Weighted scoring + historical recurrence";
    }

    @Override
    public String description() {
        return "0-100 score: 35% impact, 30% urgency, 20% criticality, 15% affected users; "
                + "+8 if the service had 3+ incidents in 24h. P1>=75, P2>=55, P3>=35.";
    }

    @Override
    public PriorityAssessment assess(PriorityContext ctx) {
        double reach = Math.min(1.0, Math.log10(Math.max(1, ctx.affectedUsers())) / 3.0);
        double base = W_IMPACT * ctx.impact().normalised()
                + W_URGENCY * ctx.urgency().normalised()
                + W_CRITICALITY * ctx.criticality().normalised()
                + W_REACH * reach;

        long recent = recentIncidentsForService(ctx.serviceId());
        boolean recurring = recent >= RECURRENCE_THRESHOLD;
        double score = Math.min(100.0, base + (recurring ? RECURRENCE_BOOST : 0));

        Priority priority = score >= 75 ? Priority.P1
                : score >= 55 ? Priority.P2
                : score >= 35 ? Priority.P3
                : Priority.P4;

        String rationale = String.format(
                "Weighted: impact %s, urgency %s, criticality %s, %d user(s) -> base %.1f%s = %.1f -> %s",
                ctx.impact(), ctx.urgency(), ctx.criticality(), ctx.affectedUsers(), base,
                recurring ? String.format(" + %.0f recurrence boost (%d incidents on service in 24h)", RECURRENCE_BOOST, recent) : "",
                score, priority.display());
        return new PriorityAssessment(priority, score, rationale, KEY);
    }

    private long recentIncidentsForService(Long serviceId) {
        if (serviceId == null) return 0;
        Instant since = clock.instant().minus(Duration.ofHours(24));
        return incidentRepository.countByAffectedServiceIdAndReportedAtAfter(serviceId, since);
    }
}
