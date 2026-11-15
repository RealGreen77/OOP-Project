package com.itsm.engine.intelligence;

import java.time.Instant;
import java.util.List;

/** Predicted likelihood that an incident will breach its resolution SLA. */
public record SlaRiskAssessment(double riskScore, RiskLevel level, Instant predictedResolution,
                                Instant deadline, List<String> factors) {

    public enum RiskLevel { LOW, MEDIUM, HIGH, BREACHED }
}
