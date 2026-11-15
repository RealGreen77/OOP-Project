package com.itsm.service;

import com.itsm.domain.catalog.ITService;
import com.itsm.domain.incident.Impact;
import com.itsm.domain.incident.Urgency;
import com.itsm.engine.priority.PriorityAssessment;
import com.itsm.engine.priority.PriorityContext;
import com.itsm.engine.priority.PriorityStrategy;
import org.springframework.stereotype.Service;

/**
 * Applies the administrator-selected {@link PriorityStrategy}.
 * // [OOP: Polymorphism] Calls {@code strategy.assess(...)} without knowing which algorithm is active.
 */
@Service
public class PriorityService {

    private final SettingsService settings;

    public PriorityService(SettingsService settings) {
        this.settings = settings;
    }

    public PriorityAssessment assess(ITService service, Impact impact, Urgency urgency, int affectedUsers) {
        PriorityStrategy strategy = settings.activePriorityStrategy();
        return strategy.assess(new PriorityContext(impact, urgency, service.getCriticality(),
                Math.max(1, affectedUsers), service.getId()));
    }
}
