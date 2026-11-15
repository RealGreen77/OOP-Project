package com.itsm.engine.priority;

import com.itsm.domain.catalog.BusinessCriticality;
import com.itsm.domain.incident.Impact;
import com.itsm.domain.incident.Urgency;

/** Inputs to priority determination. Immutable value object (Java record). */
public record PriorityContext(
        Impact impact,
        Urgency urgency,
        BusinessCriticality criticality,
        int affectedUsers,
        Long serviceId) {
}
