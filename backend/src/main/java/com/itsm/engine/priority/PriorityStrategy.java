package com.itsm.engine.priority;

import com.itsm.engine.NamedStrategy;

/**
 * Algorithm that turns impact/urgency/criticality (and more) into a P1..P4 priority.
 *
 * // [Pattern: Strategy] Interchangeable prioritisation algorithms selected at runtime by administrators.
 * // [MVP: Priority Management] [Intelligent: Intelligent Priority Determination]
 */
public interface PriorityStrategy extends NamedStrategy {

    PriorityAssessment assess(PriorityContext context);
}
