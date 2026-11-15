package com.itsm.engine.intelligence;

import com.itsm.domain.incident.Incident;
import com.itsm.domain.sla.SlaRecord;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Predicts SLA breaches before they happen.
 * <pre>
 *   expectedRemaining = max(10% of historicalMean, historicalMean - timeAlreadySpent)
 *                       x (1 + 0.15 x (assigneeActiveIncidents - 1))     -- busy technicians are slower
 *                       + 15% of the SLA window if still unassigned
 *   pressure  = min(1, expectedRemaining / timeLeftUntilDeadline)
 *   risk      = 0.5 x elapsedFraction + 0.5 x pressure
 *   HIGH >= 0.75, MEDIUM >= 0.50, else LOW
 * </pre>
 * {@code historicalMean} is the mean time-to-resolve of past incidents with the same priority and category;
 * if there is no history it defaults to half of the SLA resolution target.
 *
 * // [Intelligent Feature E] SLA Risk Detection (predictive).
 */
@Component
public class SlaRiskPredictor {

    public SlaRiskAssessment assess(Incident incident, Instant now, int assigneeActiveIncidents, Duration historicalMean) {
        SlaRecord sla = incident.getSla();
        if (sla == null || sla.getStartedAt() == null || !incident.getStatus().isActive()) return null;

        List<String> factors = new ArrayList<>();
        Instant deadline = sla.getResolutionDeadline();
        if (sla.isResolutionBreached() || now.isAfter(deadline)) {
            factors.add("Resolution deadline already passed");
            return new SlaRiskAssessment(1.0, SlaRiskAssessment.RiskLevel.BREACHED, now, deadline, factors);
        }

        Duration window = Duration.ofMinutes(sla.getResolutionTargetMinutes());
        Duration mean = historicalMean != null && !historicalMean.isZero() ? historicalMean : window.dividedBy(2);
        factors.add("Historical mean time-to-resolve: " + humanise(mean)
                + (historicalMean == null ? " (default: no history)" : ""));

        Duration spent = Duration.between(sla.getStartedAt(), now).minusSeconds(sla.getTotalPausedSeconds());
        Duration expected = mean.minus(spent);
        Duration floor = mean.dividedBy(10);
        if (expected.compareTo(floor) < 0) expected = floor;

        if (incident.getAssignedTo() == null) {
            expected = expected.plus(window.multipliedBy(15).dividedBy(100));
            factors.add("Not yet assigned");
        } else if (assigneeActiveIncidents > 1) {
            double slowdown = 1 + 0.15 * (assigneeActiveIncidents - 1);
            expected = Duration.ofSeconds((long) (expected.getSeconds() * slowdown));
            factors.add("Assignee has " + assigneeActiveIncidents + " active incidents");
        }
        if (sla.isPaused()) factors.add("SLA clock paused (waiting on reporter)");

        double elapsed = Math.min(1.0, sla.elapsedFraction(now));
        Duration left = sla.remaining(now);
        double pressure = left.isNegative() || left.isZero() ? 1.0
                : Math.min(1.0, (double) expected.getSeconds() / Math.max(1, left.getSeconds()));
        double risk = Math.round((0.5 * elapsed + 0.5 * pressure) * 1000.0) / 1000.0;
        factors.add(String.format("%.0f%% of SLA window used, %s left", elapsed * 100, humanise(left)));

        SlaRiskAssessment.RiskLevel level = risk >= 0.75 ? SlaRiskAssessment.RiskLevel.HIGH
                : risk >= 0.50 ? SlaRiskAssessment.RiskLevel.MEDIUM : SlaRiskAssessment.RiskLevel.LOW;
        Instant predicted = (sla.isPaused() ? sla.getPausedAt() : now).plus(expected);
        return new SlaRiskAssessment(risk, level, predicted, deadline, factors);
    }

    static String humanise(Duration d) {
        long minutes = Math.max(0, d.toMinutes());
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        return hours < 48 ? hours + "h " + (minutes % 60) + "m" : (hours / 24) + "d " + (hours % 24) + "h";
    }
}
