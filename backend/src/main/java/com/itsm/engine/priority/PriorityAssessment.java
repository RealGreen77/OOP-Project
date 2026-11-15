package com.itsm.engine.priority;

import com.itsm.domain.incident.Priority;

/**
 * Output of a priority strategy, including a human-readable rationale so the decision is explainable
 * (shown on the incident and recorded in the audit trail).
 */
public record PriorityAssessment(Priority priority, double score, String rationale, String strategyKey) {
}
